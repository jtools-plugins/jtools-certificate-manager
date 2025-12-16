package com.lhstack.utils;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PemUtils {

    private static final JcaPEMKeyConverter CONVERTER = new JcaPEMKeyConverter();

    static {
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (provider == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        CONVERTER.setProvider(BouncyCastleProvider.PROVIDER_NAME);
    }

    /**
     * 读取私钥
     *
     * @param keyPem PEM格式的私钥字符串
     * @return 私钥对象，如果解析失败返回null
     */
    public static PrivateKey readPrivateKey(String keyPem) throws Exception {
        if (keyPem == null || keyPem.isBlank()) {
            return null;
        }
        try (PEMParser parser = new PEMParser(new StringReader(keyPem))) {
            Object o = parser.readObject();
            if (o instanceof PEMKeyPair) {
                return CONVERTER.getPrivateKey(((PEMKeyPair) o).getPrivateKeyInfo());
            } else if (o instanceof PrivateKeyInfo) {
                return CONVERTER.getPrivateKey((PrivateKeyInfo) o);
            }
        }
        return null;
    }

    /**
     * 读取证书
     *
     * @param pem PEM格式的证书字符串
     * @return X509证书对象
     */
    public static Certificate readCertificate(String pem) throws Exception {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("证书内容不能为空");
        }
        return CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 读取证书链
     *
     * @param pem PEM格式的证书链字符串
     * @return 证书列表
     */
    @SuppressWarnings("unchecked")
    public static List<X509Certificate> readCertificateChain(String pem) throws Exception {
        if (pem == null || pem.isBlank()) {
            return new ArrayList<>();
        }
        Collection<? extends Certificate> certs = CertificateFactory.getInstance("X.509")
                .generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        List<X509Certificate> result = new ArrayList<>();
        for (Certificate cert : certs) {
            if (cert instanceof X509Certificate) {
                result.add((X509Certificate) cert);
            }
        }
        return result;
    }

    /**
     * 将对象转换为PEM格式字符串
     *
     * @param object 对象 Certificate | PrivateKey | PublicKey
     * @return PEM格式字符串
     */
    public static String toString(Object object) throws Exception {
        if (object == null) {
            throw new IllegalArgumentException("对象不能为空");
        }
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream();
             JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(bo, StandardCharsets.UTF_8))) {
            pemWriter.writeObject(object);
            pemWriter.flush();
            return bo.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * 将对象写入PEM文件
     *
     * @param object 对象 Certificate | PrivateKey | PublicKey
     * @param path   文件路径
     */
    public static void pemWriter(Object object, String path) throws Exception {
        if (object == null) {
            throw new IllegalArgumentException("对象不能为空");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(path);
             JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8))) {
            pemWriter.writeObject(object);
            pemWriter.flush();
        }
    }

    /**
     * 判断字符串是否为PEM格式
     *
     * @param content 内容
     * @return 是否为PEM格式
     */
    public static boolean isPemFormat(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.contains("-----BEGIN") && content.contains("-----END");
    }
}
