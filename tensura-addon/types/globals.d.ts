// O runtime de scripts do Minecraft expõe `console` (log/warn/error vão para o log de conteúdo).
declare var console: { log(...a: any[]): void; warn(...a: any[]): void; error(...a: any[]): void };
