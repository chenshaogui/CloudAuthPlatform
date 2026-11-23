/** pako 2.2.0 未内置类型声明，这里只声明本项目用到的两个同步 API */
declare module 'pako' {
  /** raw DEFLATE（无 zlib 头尾），与 JDK Deflater(level, true) 输出一致 */
  export function deflateRaw(data: Uint8Array | string, options?: { level?: number }): Uint8Array;
  /** raw INFLATE；to: 'string' 时按 UTF-8 还原文本 */
  export function inflateRaw(data: Uint8Array, options: { to: 'string' }): string;
  export function inflateRaw(data: Uint8Array, options?: { to?: 'array' }): Uint8Array;
}
