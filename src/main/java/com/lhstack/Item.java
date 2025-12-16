package com.lhstack;

import java.nio.charset.Charset;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Item {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Integer id;

    private String name;

    private String type;

    private String publicKeyContent;

    private String algorithm;

    private Certificate certificate;

    private String subject;

    private String issuer;

    private Date notBefore;

    private Date notAfter;

    private boolean expired;

    public Item setCertificate(Certificate certificate) {
        this.certificate = certificate;
        if (certificate instanceof X509Certificate) {
            X509Certificate x509 = (X509Certificate) certificate;
            this.subject = x509.getSubjectX500Principal().getName();
            this.issuer = x509.getIssuerX500Principal().getName();
            this.notBefore = x509.getNotBefore();
            this.notAfter = x509.getNotAfter();
            try {
                x509.checkValidity();
                this.expired = false;
            } catch (Exception e) {
                this.expired = true;
            }
        }
        return this;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public String getPublicKeyContent() {
        return publicKeyContent;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getType() {
        return type;
    }

    public Item setType(String type) {
        this.type = type;
        return this;
    }

    public Integer getId() {
        return id;
    }

    public Item setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public Item setName(String name) {
        this.name = name;
        return this;
    }

    public Item setPublicKey(PublicKey publicKey, Charset charset) {
        if (publicKey != null) {
            this.publicKeyContent = new String(publicKey.getEncoded(), charset);
            this.algorithm = publicKey.getAlgorithm();
        }
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public String getIssuer() {
        return issuer;
    }

    public Date getNotBefore() {
        return notBefore;
    }

    public Date getNotAfter() {
        return notAfter;
    }

    public boolean isExpired() {
        return expired;
    }

    public String getNotAfterFormatted() {
        return notAfter != null ? DATE_FORMAT.format(notAfter) : "";
    }

    public String getNotBeforeFormatted() {
        return notBefore != null ? DATE_FORMAT.format(notBefore) : "";
    }

    /**
     * 获取证书状态描述
     */
    public String getStatus() {
        if (expired) {
            return "已过期";
        }
        if (notAfter != null) {
            long daysRemaining = (notAfter.getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
            if (daysRemaining <= 30) {
                return "即将过期(" + daysRemaining + "天)";
            }
        }
        return "有效";
    }
}
