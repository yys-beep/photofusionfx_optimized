package com.photofusionfx.service;

import com.photofusionfx.model.EmailConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

public class EmailService {
    public EmailConfig loadConfig() throws IOException {
        EmailConfig config = new EmailConfig();
        if (!Files.exists(AppPaths.MAIL_CONFIG_PATH)) {
            return config;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(AppPaths.MAIL_CONFIG_PATH)) {
            properties.load(in);
        }
        config.setSmtpHost(properties.getProperty("smtp.host", config.getSmtpHost()));
        config.setSmtpPort(Integer.parseInt(properties.getProperty("smtp.port", String.valueOf(config.getSmtpPort()))));
        config.setStartTls(Boolean.parseBoolean(properties.getProperty("smtp.starttls", String.valueOf(config.isStartTls()))));
        config.setSsl(Boolean.parseBoolean(properties.getProperty("smtp.ssl", String.valueOf(config.isSsl()))));
        config.setUsername(properties.getProperty("smtp.username", ""));
        config.setPassword(properties.getProperty("smtp.password", ""));
        config.setFrom(properties.getProperty("smtp.from", ""));
        return config;
    }

    public void saveConfig(EmailConfig config) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("smtp.host", config.getSmtpHost());
        properties.setProperty("smtp.port", String.valueOf(config.getSmtpPort()));
        properties.setProperty("smtp.starttls", String.valueOf(config.isStartTls()));
        properties.setProperty("smtp.ssl", String.valueOf(config.isSsl()));
        properties.setProperty("smtp.username", config.getUsername());
        properties.setProperty("smtp.password", config.getPassword());
        properties.setProperty("smtp.from", config.getFrom());
        try (OutputStream out = Files.newOutputStream(AppPaths.MAIL_CONFIG_PATH)) {
            properties.store(out, "PhotoFusion FX mail settings");
        }
    }

    public void sendEmail(EmailConfig config,
                          String to,
                          String subject,
                          String body,
                          File attachment) throws MessagingException, IOException {
        Properties props = new Properties();
        boolean auth = config.getUsername() != null && !config.getUsername().isBlank();
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.host", config.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(config.getSmtpPort()));
        props.put("mail.smtp.starttls.enable", String.valueOf(config.isStartTls()));
        props.put("mail.smtp.ssl.enable", String.valueOf(config.isSsl()));

        Session session = auth
                ? Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(config.getUsername(), config.getPassword());
                    }
                })
                : Session.getInstance(props);

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(config.getFrom()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject, "UTF-8");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(body == null ? "" : body, "UTF-8");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);

        if (attachment != null) {
            MimeBodyPart filePart = new MimeBodyPart();
            filePart.attachFile(attachment);
            multipart.addBodyPart(filePart);
        }

        message.setContent(multipart);
        Transport.send(message);
    }
}
