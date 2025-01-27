# Circuit Breaker Serverless na AWS

Este repositório contém a implementação de um **circuit breaker serverless** na AWS. A solução utiliza um API Gateway, funções Lambda (principais e fallbacks), alarmes do CloudWatch e um arquivo de configuração armazenado no S3 para gerenciar alterações de rota entre funções.

## Descrição Geral

A função Lambda deste repositório é responsável por:
- Monitorar o estado de alarmes do CloudWatch.
- Consultar as configurações de roteamento no S3.
- Alterar dinamicamente a rota do API Gateway entre uma função Lambda principal e um fallback, dependendo do estado do alarme (ALARM ou OK).

## Estrutura do Arquivo S3

O arquivo de configuração armazenado no S3 deve estar no seguinte formato:

```json
{
  "alarms": [
    {
      "alarmName": "nome do alarme",
      "apiId": "id do API Gateway",
      "resourceId": "id do recurso a ser trocado",
      "httpMethod": "qual é o verbo http do método",
      "mainLambdaArn": "arn da função lambda principal",
      "fallbackLambdaArn": "arn do fallback",
      "stageName": "nome do stage no API Gateway onde ocorrerá a troca"
    }
  ]
}
```

### Observações:
- O **nome do alarme** no arquivo deve ser igual ao nome no CloudWatch.
- Se o alarme não for encontrado no arquivo S3, a troca de rota não será realizada.

## Configuração da Lambda

### Variáveis de Ambiente

- `REGION`: Região AWS onde estão os serviços.
- `S3_BUCKET_NAME`: Nome do bucket S3 contendo o arquivo de configuração.
- `S3_FILE_KEY`: Caminho do arquivo de configuração no bucket S3.

### Funcionamento

1. A Lambda é acionada pelo alarme do CloudWatch.
2. Verifica o estado do alarme (ALARM ou OK).
3. Consulta o arquivo S3 para obter os detalhes do alarme e da configuração de rota.
4. Atualiza a rota do API Gateway para apontar para a função principal ou fallback, dependendo do estado do alarme.
5. Redeply no stage do API Gateway para aplicar as mudanças.

## Código Principal

A lógica principal está implementada na classe `TrocadorDeRotaLambda`. Os principais passos são:

1. **Receber e processar o evento do alarme:**
   - Extrai o nome e o estado do alarme do evento.
2. **Consultar o S3:**
   - Busca a configuração do alarme pelo nome no arquivo S3.
3. **Atualizar o API Gateway:**
   - Modifica a integração do recurso para apontar para a função Lambda correspondente.
4. **Redeply no Stage:**
   - Aplica as mudanças no stage do API Gateway.

## Requisitos para Funcionamento

- **CloudWatch Alarm:** Configurado para monitorar a métrica desejada (e.g., latência ou erros).
- **API Gateway:** Configurado com os recursos e métodos corretos para suportar a troca de rota.
- **S3 Bucket:** Contendo o arquivo de configuração atualizado.

## Exemplo de Fluxo

1. O alarme do CloudWatch detecta um problema (e.g., alta latência) e entra no estado **ALARM**.
2. O CloudWatch aciona a função `TrocadorDeRotaLambda`.
3. A função consulta o S3, encontra a configuração do alarme e troca a rota do API Gateway para apontar para a função fallback.
4. Quando o alarme retorna ao estado **OK**, a rota é restaurada para a função principal.

## Observações Finais

- Certifique-se de que as permissões do S3, CloudWatch e API Gateway estão configuradas corretamente.
- Para dúvidas ou problemas, abra uma issue neste repositório.

---

Desenvolvido para demonstrar uma abordagem serverless resiliente e eficiente para sistemas baseados em eventos.

