package io.github.astail.notescope;

/**
 * Minecraft 標準フォントのグリフ横幅（GUI ピクセル, 文字間 1px 込み）を概算するユーティリティ。
 *
 * <p>アクションバーはプロポーショナルフォント（文字ごとに横幅が異なる）で描画され、さらに
 * 画面中央に寄せて表示される。そのため音名・調律の桁数・楽器名などの幅が変わると、
 * 後続の項目や行全体の位置がずれてしまう。
 *
 * <p>各列を {@link #padRight(String, int)} で固定幅まで半角スペースで右詰めパディングすると、
 * 続く項目の開始位置と行の総幅が一定になり、表示位置のずれを抑えられる。
 * 半角スペースは標準フォントで 4px のため、揃えの粒度は最大 4px（＝完全な 1px 一致は不可）。
 */
final class FontWidth {

    /** 半角スペースの横幅（px）。パディングの最小単位。 */
    private static final int SPACE = 4;

    /** 数字・記号・英字などの標準的なグリフ幅（px, 5px グリフ + 1px 文字間）。 */
    private static final int DEFAULT_ASCII = 6;

    /**
     * 全角（カナ・漢字）文字の横幅（px）。標準フォントの実測値は版に依存するため、
     * 表示のずれが目立つ場合はこの値を実機を見ながら微調整する（おおむね 8〜9px）。
     */
    private static final int FULLWIDTH = 8;

    private FontWidth() {
    }

    /** 文字列の表示幅（px）を概算する。 */
    static int width(String text) {
        int total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            total += charWidth(cp);
            i += Character.charCount(cp);
        }
        return total;
    }

    /**
     * {@code text} の末尾に半角スペースを足し、表示幅を {@code targetPx} へ近づける。
     * スペースは 4px 刻みのため、結果は targetPx に対して最大 ±数px の残差を持つ。
     */
    static String padRight(String text, int targetPx) {
        int pad = Math.max(0, Math.round((targetPx - width(text)) / (float) SPACE));
        return pad == 0 ? text : text + " ".repeat(pad);
    }

    /** コードポイント 1 文字の横幅（px, 文字間 1px 込み）を返す。 */
    private static int charWidth(int cp) {
        return switch (cp) {
            case ' ' -> SPACE;
            case ':' -> 2;
            case '(', ')' -> 5;
            default -> {
                if (cp < 0x80) {
                    yield DEFAULT_ASCII; // 数字・英字・# / ( ) : 以外の記号などの ASCII を概算
                }
                if (cp == '♪') {
                    yield DEFAULT_ASCII; // 行頭で常に同じため厳密値は整列に影響しない
                }
                yield FULLWIDTH; // 全角（カナ・漢字）
            }
        };
    }
}
