# Compilar o HÉLIOS pelo navegador do celular

1. Abra a aba **Actions** deste repositório.
2. Selecione **Build HÉLIOS APK**.
3. Toque em **Run workflow** e confirme.
4. Quando terminar, abra a execução e baixe o artefato **helios-debug-apk**.
5. Extraia o ZIP e localize `app-debug.apk`.

O APK é de depuração. O app oferece conversa local, voz sob demanda e atalhos Android. Para respostas de IA online, configure e hospede o backend Node em `backend/`, defina a variável `GEMINI_API_KEY` no ambiente do servidor e informe a URL HTTPS do servidor em Configurações no app. Não publique chaves no GitHub.

A palavra de ativação contínua e a execução automática em segundo plano ainda não fazem parte desta versão. O microfone só é ativado quando o usuário toca no botão.
