/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.jms.transports;

import io.netty.handler.ssl.SslHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static class that provides various utility methods used by Transport implementations.
 */
public class TransportSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TransportSupport.class);

    /**
     * Creates a Netty SslHandler instance for use in Transports that require
     * an SSL encoder / decoder.
     *
     * @param options
     *        The SSL options object to build the SslHandler instance from.
     *
     * @return a new SslHandler that is configured from the given options.
     *
     * @throws Exception if an error occurs while creating the SslHandler instance.
     */
    public static SslHandler createSslHandler(TransportSslOptions options) throws Exception {
        return new SslHandler(createSslEngine(createSslContext(options), options));
    }

    public static SSLContext createSslContext(TransportSslOptions options) throws Exception {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            KeyManager[] keyMgrs = loadKeyManagers(options);

            TrustManager[] trustManagers;
            if (options.isTrustAll()) {
                trustManagers = new TrustManager[] { createTrustAllTrustManager() };
            } else {
                trustManagers = loadTrustManagers(options);
            }

            context.init(keyMgrs, trustManagers, new SecureRandom());
            return context;
        } catch (Exception e) {
            LOG.error("Failed to create SSLContext: {}", e, e);
            throw e;
        }
    }

    public static SSLEngine createSslEngine(SSLContext context, TransportSslOptions options) throws Exception {
        SSLEngine engine = context.createSSLEngine();
        engine.setEnabledProtocols(options.getEnabledProtocols());
        engine.setUseClientMode(true);

        if (options.isVerifyHost()) {
            SSLParameters sslParameters = engine.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(sslParameters);
        }

        return engine;
    }

    private static TrustManager[] loadTrustManagers(TransportSslOptions options) throws Exception {
        if (options.getTrustStoreLocation() == null) {
            return null;
        }

        TrustManagerFactory fact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        String storeLocation = options.getKeyStoreLocation();
        String storePassword = options.getKeyStorePassword();
        String storeType = options.getStoreType();

        LOG.trace("Attempt to load KeyStore from location {} of type {}", storeLocation, storeType);

        KeyStore trustStore = loadStore(storeLocation, storePassword, storeType);
        fact.init(trustStore);

        return fact.getTrustManagers();
    }

    private static KeyManager[] loadKeyManagers(TransportSslOptions options) throws Exception {
        if (options.getKeyStoreLocation() == null) {
            return null;
        }

        KeyManagerFactory fact = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

        String storeLocation = options.getKeyStoreLocation();
        String storePassword = options.getKeyStorePassword();
        String storeType = options.getStoreType();

        LOG.trace("Attempt to load KeyStore from location {} of type {}", storeLocation, storeType);

        KeyStore keyStore = loadStore(storeLocation, storePassword, storeType);
        fact.init(keyStore, storePassword != null ? storePassword.toCharArray() : null);

        return fact.getKeyManagers();
    }

    private static KeyStore loadStore(String storePath, final String password, String storeType) throws Exception {
        KeyStore store = KeyStore.getInstance(storeType);
        try (InputStream in = new FileInputStream(new File(storePath));) {
            store.load(in, password != null ? password.toCharArray() : null);
        }

        return store;
    }

    private static TrustManager createTrustAllTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}