# Backend HÉLIOS

## Publicação no Render
1. Crie uma conta no Render e escolha New → Blueprint.
2. Conecte este repositório e selecione o arquivo `render.yaml` na raiz.
3. Quando solicitado, informe sua chave de API do Gemini no campo secreto `GEMINI_API_KEY`. Não a coloque no código, no APK, em mensagens ou em commits.
4. Aguarde o serviço ficar Live. Copie a URL HTTPS gerada, por exemplo `https://helios-backend.onrender.com`.
5. No app HÉLIOS, abra Configurações, cole a URL HTTPS e salve.
6. Teste a URL acrescentando `/health`; a resposta esperada é JSON com `"ok":true`.

O plano gratuito do provedor de hospedagem pode suspender serviços inativos ou mudar suas condições. A primeira chamada depois de um período parado pode demorar. A disponibilidade e os limites gratuitos da API Gemini dependem da conta, região e política atual do Google.

## Execução local
`npm install`, copie `.env.example` para `.env`, configure `GEMINI_API_KEY` e execute `npm start`.
