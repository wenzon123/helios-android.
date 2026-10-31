# HÉLIOS — assistente pessoal Android

Aplicativo Android nativo em Kotlin + Jetpack Compose, em português brasileiro.

## Recursos da primeira versão
- Interface escura com identidade visual HÉLIOS.
- Conversa local básica.
- Entrada por voz usando o reconhecedor de fala do Android (acionada somente pelo usuário).
- Resposta falada usando TextToSpeech.
- Atalhos autorizados para configurações, pesquisa na web e YouTube.
- Histórico local simples.
- Build de APK debug pelo GitHub Actions.

## Compilar
Abra **Actions → Build HÉLIOS APK → Run workflow** ou envie um commit para `main`. O artefato `helios-debug-apk` contém `app-debug.apk`.

## Limites desta versão
Esta é a fundação funcional do app, não um assistente sempre ouvindo. A palavra de ativação, IA integrada, memória avançada e automações adicionais serão implementadas em etapas. O reconhecimento de voz por botão e a leitura de respostas já estão no código. Não há um provedor de IA conectado nesta versão. Não coloque chaves de API no aplicativo nem no repositório.
