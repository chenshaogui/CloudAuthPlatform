# Web console

This Vue 3 console is based on [RuoYi-Vue-Plus](https://gitee.com/dromara/RuoYi-Vue-Plus). See the included license and upstream notices.

## Local development

1. Copy `.env.example` to `.env.development.local` and set the API URL, client ID, and deployment-specific public key.
2. Install dependencies with `npm install`.
3. Start the development server with `npm run dev`.

Do not commit local environment files or private keys. Vite embeds `VITE_*` values in browser assets; the existing response-decryption flow must not be configured with a production private key in a public build.
