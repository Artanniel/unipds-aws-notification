package com.artantech.unipds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

public class NotificationHandler implements RequestHandler<S3Event, String> {

    @Override
    public String handleRequest(S3Event input, Context context) {
        try {
            if (input == null || input.getRecords() == null || input.getRecords().isEmpty()) {
                context.getLogger().log("No records found in S3Event");
                return "No records";
            }

            S3EventNotificationRecord record = input.getRecords().get(0);
            String bucket = record.getS3().getBucket().getName();
            String key = record.getS3().getObject().getKey();

            context.getLogger().log("Bucket: " + bucket + ", Key: " + key);

            String remetente = getParam("/notificacao/email/user", true);
            String senha = getParam("/notificacao/email/pass", true);
            String destinatario = getParam("/app/email/rh", false);

            if (remetente != null) remetente = remetente.trim();
            if (senha != null) senha = senha.replaceAll("\\s+", "");
            if (destinatario != null) destinatario = destinatario.trim();

            context.getLogger().log("Remetente obtido: " + remetente);
            context.getLogger().log("Destinatario obtido: " + destinatario);

            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost("smtp.gmail.com");
            mailSender.setPort(587);
            mailSender.setUsername(remetente);
            mailSender.setPassword(senha);

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");
            props.put("mail.debug", "true");

            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(remetente);
            mail.setTo(destinatario);
            mail.setSubject("Novo arquivo enviado para o bucket " + bucket);
            mail.setText("Arquivo: " + key);

            mailSender.send(mail);

            context.getLogger().log("Mensagem enviada com sucesso para " + destinatario);
            return "Success";

        } catch (Exception e) {
            context.getLogger().log("Processo falhou com erro: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private String getParam(String paramName, boolean withDecryption) {
        AWSSimpleSystemsManagement ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient();
        GetParameterRequest request = new GetParameterRequest()
                .withName(paramName)
                .withWithDecryption(withDecryption);
        GetParameterResult result = ssmClient.getParameter(request);
        return result.getParameter().getValue();
    }
}
