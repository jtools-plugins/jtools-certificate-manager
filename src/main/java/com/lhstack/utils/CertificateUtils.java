package com.lhstack.utils;

import com.intellij.openapi.vfs.VirtualFile;
import com.lhstack.Item;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class CertificateUtils {

    private static final String BC_PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

    static {
        Provider provider = Security.getProvider(BC_PROVIDER);
        if (provider == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 导出证书
     *
     * @param keyStoreSupplier  KeyStore提供者
     * @param passwordSupplier  密码提供者
     * @param items             证书项列表
     * @param type              导出类型
     * @return 导出的字节数组
     */
    public static byte[] export(Supplier<KeyStore> keyStoreSupplier, Supplier<char[]> passwordSupplier, List<Item> items, String type) throws Exception {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("请选择要导出的证书");
        }
        type = type == null ? "jks" : type.toLowerCase(Locale.ROOT);
        switch (type) {
            case "pem":
                return pemExport(items);
            case "cer":
            case "crt":
            case "der":
                if (items.size() > 1) {
                    throw new RuntimeException("crt/cer/der格式只支持单个证书导出");
                }
                return crtExport(items);
            case "pkcs8":
                if (items.size() > 1) {
                    throw new RuntimeException("pkcs8格式只支持单个证书导出");
                }
                return pkcs8Export(keyStoreSupplier, items);
            case "p12":
            case "pfx":
                return pkcs12Export(items);
            case "p7b":
            case "p7c":
                return pkcs7Export(items);
            case "jks":
            default:
                return jksExport(items);
        }
    }

    /**
     * 导出为PKCS7格式 (证书链)
     */
    private static byte[] pkcs7Export(List<Item> items) throws Exception {
        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
        List<Certificate> certs = new java.util.ArrayList<>();
        for (Item item : items) {
            certs.add(item.getCertificate());
        }
        java.security.cert.CertPath certPath = cf.generateCertPath(certs);
        return certPath.getEncoded("PKCS7");
    }

    private static byte[] pkcs8Export(Supplier<KeyStore> keyStoreSupplier, List<Item> items) throws Exception {
        Item item = items.get(0);
        String password = JOptionPane.showInputDialog("请输入私钥密码");
        char[] passwordChars = (password == null || password.isEmpty()) ? new char[0] : password.toCharArray();
        Key key = keyStoreSupplier.get().getKey(item.getName(), passwordChars);
        if (key instanceof PrivateKey) {
            return new PKCS8EncodedKeySpec(key.getEncoded()).getEncoded();
        }
        throw new RuntimeException("不支持导出pkcs8的证书类型,请检查证书是否存在私钥");
    }

    private static byte[] crtExport(List<Item> items) throws Exception {
        Item item = items.get(0);
        return item.getCertificate().getEncoded();
    }

    private static byte[] jksExport(List<Item> items) throws Exception {
        return exportKeyStore(items, "JKS", "jks");
    }

    private static byte[] pkcs12Export(List<Item> items) throws Exception {
        return exportKeyStore(items, "PKCS12", "p12");
    }

    private static byte[] exportKeyStore(List<Item> items, String keyStoreType, String typeName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        for (Item item : items) {
            keyStore.setCertificateEntry(item.getName(), item.getCertificate());
        }
        String result = JOptionPane.showInputDialog(
                String.format("请输入需要导出的%s文件的密码,如果没有,则点击取消或者不输入直接确认", typeName),
                "changeit"
        );
        char[] passwordChars = StringUtils.isNotBlank(result) ? result.toCharArray() : new char[0];
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            keyStore.store(bo, passwordChars);
            return bo.toByteArray();
        }
    }

    private static byte[] pemExport(List<Item> items) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            for (Item item : items) {
                pemWriter.writeObject(item.getCertificate());
            }
            pemWriter.flush();
            return baos.toByteArray();
        }
    }

    /**
     * 从文件加载证书
     *
     * @param virtualFile 虚拟文件
     * @return 证书对象
     */
    public static Certificate load(VirtualFile virtualFile) throws Exception {
        if (virtualFile == null) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String extension = virtualFile.getExtension();
        if (extension == null) {
            throw new RuntimeException("无法识别文件类型,请确保文件有正确的扩展名");
        }
        extension = extension.toLowerCase(Locale.ROOT);
        if (StringUtils.equalsAny(extension, "crt", "pem", "cer", "der")) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream inputStream = virtualFile.getInputStream()) {
                return cf.generateCertificate(inputStream);
            }
        }
        throw new RuntimeException("导入证书仅支持crt,pem,cer,der格式证书");
    }

    /**
     * 获取证书的简要信息
     *
     * @param certificate 证书
     * @return 简要信息字符串
     */
    public static String getCertificateSummary(Certificate certificate) {
        if (certificate instanceof X509Certificate) {
            X509Certificate x509 = (X509Certificate) certificate;
            return String.format("主题: %s\n颁发者: %s\n有效期: %s 至 %s\n序列号: %s",
                    x509.getSubjectX500Principal().getName(),
                    x509.getIssuerX500Principal().getName(),
                    x509.getNotBefore(),
                    x509.getNotAfter(),
                    x509.getSerialNumber().toString(16));
        }
        return certificate.toString();
    }

    /**
     * 检查证书是否过期
     *
     * @param certificate 证书
     * @return 是否过期
     */
    public static boolean isExpired(Certificate certificate) {
        if (certificate instanceof X509Certificate) {
            try {
                ((X509Certificate) certificate).checkValidity();
                return false;
            } catch (Exception e) {
                return true;
            }
        }
        return false;
    }
}
