package com.gp.updater.utils;

import android.content.Context;

import com.gp.updater.R;

import android.util.Log;

import android.net.SSLCertificateSocketFactory;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/**
 * Created by dragorn on 5/20/14.
 */
public class BlackphoneHttpClient extends DefaultHttpClient {
    private static final boolean SNI_ENABLE = true;
    final Context context;

    final static HostnameVerifier hostnameVerifier = new StrictHostnameVerifier();


    public BlackphoneHttpClient(Context c) {
        context = c;
    }

    @Override
    protected ClientConnectionManager createClientConnectionManager() {
        SchemeRegistry registry = new SchemeRegistry();

        /* We don't allow http at all
        registry.register(
                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
                */

        if (SNI_ENABLE) {
            registry.register(new Scheme("https", new BlackphoneTlsSniSocketFactory(), 443));
        } else {
            registry.register(new Scheme("https", BlackphoneSslSocketFactory(), 443));
        }
        return new ThreadSafeClientConnManager(getParams(), registry);
    }

    private KeyStore obtainKeyStore() throws KeyStoreException,
            CertificateException, NoSuchAlgorithmException, IOException {


        KeyStore trusted = KeyStore.getInstance("BKS");

        InputStream in = context.getResources().openRawResource(R.raw.blackphone);
        try {
            trusted.load(in, "blackphone".toCharArray());
        } finally {
            in.close();
        }
        return trusted;
    }

    private SSLSocketFactory BlackphoneSslSocketFactory() {
        try {
            KeyStore trusted = KeyStore.getInstance("BKS");
            InputStream in = context.getResources().openRawResource(R.raw.blackphone);
            try {
                trusted.load(in, "blackphone".toCharArray());
            } finally {
                in.close();
            }
            return new SSLSocketFactory(trusted);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public class BlackphoneTlsSniSocketFactory implements LayeredSocketFactory {
        private static final String TAG = "BlackphoneTlsSniSocketFactory";

        @Override
        public Socket createSocket() throws IOException {
            return null;
        }

        @Override
        public Socket connectSocket(Socket socket, String s, int i, InetAddress inetAddress,
                                    int i2, HttpParams httpParams)
                throws IOException, UnknownHostException, ConnectTimeoutException {
            return null;
        }

        @Override
        public boolean isSecure(Socket s) throws IllegalArgumentException {
            if (s instanceof SSLSocket)
                return ((SSLSocket) s).isConnected();
            return false;
        }


        // TLS layer

        @Override
        public Socket createSocket(Socket plainSocket, String host, int port, boolean autoClose)
                throws IOException, UnknownHostException {
            if (autoClose) {
                // we don't need the plainSocket
                plainSocket.close();
            }

            try {
                // create and connect SSL socket, but don't do hostname/certificate verification yet
                SSLCertificateSocketFactory sslSocketFactory =
                        (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(0);

                KeyStore trusted = obtainKeyStore();

                TrustManagerFactory trustManagerFactory = TrustManagerFactory
                        .getInstance(TrustManagerFactory.getDefaultAlgorithm());

                trustManagerFactory.init(trusted);

                sslSocketFactory.setTrustManagers(trustManagerFactory.getTrustManagers());

                SSLSocket ssl = (SSLSocket) sslSocketFactory
                        .createSocket(InetAddress.getByName(host), port);

                // enable TLSv1.1/1.2 if available
                // (see https://github.com/rfc2822/davdroid/issues/229)
                ssl.setEnabledProtocols(ssl.getSupportedProtocols());

                // set up SNI before the handshake
                sslSocketFactory.setHostname(ssl, host);


                // verify hostname and certificate
                SSLSession session = ssl.getSession();
                if (!hostnameVerifier.verify(host, session))
                    throw new SSLPeerUnverifiedException("Cannot verify hostname: " + host);

                return ssl;
            } catch (Exception e) {
                Log.d(TAG, "error ", e);
            }
            return null;
        }
    }
}
