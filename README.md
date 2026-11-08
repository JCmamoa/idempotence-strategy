# DLQ Console — MVP local

Backoffice local para inspecionar eventos que esgotaram as tentativas e solicitar reprocessamento auditado.

## Subir o projeto

Requer Docker Engine com Docker Compose:

```bash
docker compose up --build
```

- Interface: http://localhost:5173
- API: http://localhost:8080/api/health
- RabbitMQ Management: http://localhost:15672 (`guest` / `guest`)
- PostgreSQL: `localhost:5432` (`dlq_admin` / `local-dev-password`)

O RabbitMQ usa duas filas de retry com TTL de 1 e 5 minutos. Para testar o fluxo completo sem aguardar, defina `APP_RETRY_DELAY_1=5000` e `APP_RETRY_DELAY_2=10000` no serviço `backend` do `docker-compose.yml` e recrie os serviços.

## Fluxo

1. Publique um evento pela interface. A opção de falha simulada vem ativa para demonstrar o fluxo.
2. O worker tenta processar o evento, encaminha falhas para `events.retry.1` e `events.retry.2` e, após três falhas, publica em `events.dlq`.
3. O consumidor da DLQ persiste a mensagem no PostgreSQL e ela aparece no painel.
4. Informe o motivo e solicite o reprocessamento. O MVP publica novamente na fila principal e registra a solicitação na tabela `replay_audit`.

Eventos com formato inválido são rejeitados sem requeue e roteados automaticamente para a DLQ via DLX. O backoffice persiste o corpo original, permitindo que a engenharia examine o evento.

## Desenvolvimento local sem Docker

Suba PostgreSQL e RabbitMQ localmente, então execute:

```bash
cd backend && mvn spring-boot:run
```

Em outro terminal:

```bash
cd frontend && npm install && npm run dev
```

## Limites do MVP

Este projeto é apenas para desenvolvimento local: não inclui autenticação/RBAC, armazenamento externo de payloads, outbox transacional, política de retenção nem integração com Slack/PagerDuty. Não exponha os serviços nem use as credenciais de desenvolvimento em produção. O payload fica armazenado em texto no banco; use dados fictícios.
