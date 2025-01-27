package circuito;

import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Collections;

public class TrocadorDeRotaLambda implements RequestHandler<Object, String> {

    private String region;
    private String s3BucketName;
    private String s3FileKey;

    private final AmazonApiGateway apiGatewayClient;
    private final AmazonS3 s3Client;
    private final ObjectMapper objectMapper;

    public TrocadorDeRotaLambda() {

        this.region = System.getenv("REGION");
        this.s3BucketName = System.getenv("S3_BUCKET_NAME");
        this.s3FileKey = System.getenv("S3_FILE_KEY");

        if (region == null || s3BucketName == null || s3FileKey == null) {
            throw new IllegalArgumentException("É preciso configurar as variaveis de região(REGION), nome do bucket(S3_BUCKET_NAME) e chave do arquivo S3(S3_FILE_KEY) .");
        }

        this.apiGatewayClient = AmazonApiGatewayClientBuilder.standard()
                .withRegion(region)
                .build();
        this.s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String handleRequest(Object event, Context context) {
        context.getLogger().log("Evento recebido: " + event);

        try {
            JsonNode eventNode = objectMapper.readTree(objectMapper.writeValueAsString(event));
            String nomeAlarme = eventNode.at("/alarmData/alarmName").asText();
            String estadoAlarme = eventNode.at("/alarmData/state/value").asText();

            if (nomeAlarme.isEmpty() || estadoAlarme.isEmpty()) {
                throw new RuntimeException("O nome do alarme e/ou estado do alarme não estão presentes para realizar a chamada.");
            }

            context.getLogger().log("Nome do alarme: " + nomeAlarme + ", estado do alarme: " + estadoAlarme);

            InputStream s3FileStream = s3Client.getObject(s3BucketName, s3FileKey).getObjectContent();
            JsonNode configRoot = objectMapper.readTree(s3FileStream);

            JsonNode alarms = configRoot.get("alarms");
            JsonNode alarmeS3 = null;
            for (JsonNode alarm : alarms) {
                if (alarm.get("alarmName").asText().equals(nomeAlarme)) {
                    alarmeS3 = alarm;
                    break;
                }
            }
            context.getLogger().log("alarmeS3: " + alarmeS3);

            if (alarmeS3 == null) {
                throw new RuntimeException("Não foi encontrado nenhum alarme com o seguinte nome: " + nomeAlarme);
            }

            String targetLambdaArn = estadoAlarme.equals("ALARM")
                    ? alarmeS3.get("fallbackLambdaArn").asText()
                    : alarmeS3.get("mainLambdaArn").asText();

            String apiId = alarmeS3.get("apiId").asText();
            String resourceId = alarmeS3.get("resourceId").asText();
            String httpMethod = alarmeS3.get("httpMethod").asText();
            String stageName = alarmeS3.get("stageName").asText();

            context.getLogger().log("Fazendo a troca da rota com os valores: API ID: " + apiId + ", Resource ID: " + resourceId + ", nova rota: " + targetLambdaArn);

            UpdateIntegrationResult updateResponse = apiGatewayClient.updateIntegration(new UpdateIntegrationRequest()
                    .withRestApiId(apiId)
                    .withResourceId(resourceId)
                    .withHttpMethod(httpMethod)
                    .withPatchOperations(Collections.singletonList(
                            new PatchOperation()
                                    .withOp(Op.Replace)
                                    .withPath("/uri")
                                    .withValue("arn:aws:apigateway:" + region + ":lambda:path/2015-03-31/functions/" + targetLambdaArn + "/invocations")
                    )));

            context.getLogger().log("API Gateway atualizado: " + updateResponse);

            CreateDeploymentResult deploymentResponse = apiGatewayClient.createDeployment(new CreateDeploymentRequest()
                    .withRestApiId(apiId)
                    .withStageName(stageName));

            context.getLogger().log("Stage '" + stageName + "' redeployed: " + deploymentResponse);

            return "Rota atualizada com sucesso " + nomeAlarme;

        } catch (Exception e) {
            context.getLogger().log("Erro ao processar o alarme: " + e.getMessage());
            throw new RuntimeException("Falha ao atualizar a rota do API Gateway", e);
        }
    }
}