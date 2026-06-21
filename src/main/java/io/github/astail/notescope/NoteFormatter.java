package io.github.astail.notescope;

import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Instrument;
import org.bukkit.Note;

/**
 * {@link Note} と {@link Instrument} を、人が読める音階表記のアクションバー用 Component に整形する。
 *
 * <p>音符ブロックの音は 0〜24 の 25 段階（F#3〜F#5 の 2 オクターブ）。
 * バニラでは設置直後が 0（F#3）で、右クリックごとに +1 され 24 で 0 に戻る。
 *
 * <p><b>注意（起動初期に参照しないこと）:</b> 本クラスの static 初期化ブロックは列幅算出のため
 * {@link Instrument#values()} を呼び、これが {@code Instrument →（Sound → Registry）} のクラス
 * 初期化を誘発する。つまり本クラスの初期化はサーバーの RegistryAccess が用意済みであることに依存する。
 * 現状は {@code NoteLookTask}（ゲームプレイ中のスケジュールタスク）からのみ参照されるためレジストリは
 * 確実に初期化済みで問題ないが、{@code onEnable} 等の起動初期（レジストリ準備前）に本クラスを参照すると
 * 初期化順次第で {@link ExceptionInInitializerError} になり得る。将来そうした早期参照が必要になった場合は、
 * 列幅算出を初回 {@link #format} 呼び出し時の遅延初期化に切り替え、クラス初期化からレジストリ依存を外すこと。
 */
final class NoteFormatter {

    /**
     * 各列を揃えるための目標幅（px）。取りうる全ての音・楽器を走査して最大幅を求める。
     * 各列をこの幅まで右詰めパディングすることで、後続項目の開始位置と行の総幅が一定になり、
     * 「♪」「調律」「楽器:」の表示位置がずれなくなる（音名・楽器名が変わっても固定）。
     */
    private static final int COL_NOTE_WIDTH;   // 「♪ 音名 (ドレミ)」
    private static final int COL_TUNING_WIDTH; // 「  調律 n/24」
    private static final int COL_INSTRUMENT_WIDTH; // 「  楽器: 名前」

    static {
        int noteW = 0;
        int tuningW = 0;
        int instrumentW = 0;
        for (int id = 0; id <= 24; id++) {
            Note note = new Note(id);
            noteW = Math.max(noteW, FontWidth.width(noteHead(note) + solfegePart(note)));
            tuningW = Math.max(tuningW, FontWidth.width(tuningSegment(id)));
        }
        // Instrument.values() は Instrument →（Sound → Registry）の初期化を誘発し、
        // この static ブロックがサーバーの RegistryAccess 準備済みに依存する原因となる。
        // そのため本クラスを起動初期（レジストリ準備前）に参照しないこと（クラス Javadoc 参照）。
        for (Instrument instrument : Instrument.values()) {
            instrumentW = Math.max(instrumentW, FontWidth.width(instrumentSegment(instrument)));
        }
        COL_NOTE_WIDTH = noteW;
        COL_TUNING_WIDTH = tuningW;
        COL_INSTRUMENT_WIDTH = instrumentW;
    }

    private NoteFormatter() {
    }

    /**
     * 例: 「♪ F#3 (ファ#)  調律 13/24  楽器: ハープ」
     *
     * <p>各列を固定幅まで半角スペースでパディングし、視点を移しても「♪」「調律」「楽器:」が
     * 横にずれないようにする。パディングの不可視スペースは各色の成分末尾に付与する。
     *
     * @param note       音符ブロックの音（0〜24）
     * @param instrument 音符ブロックの楽器（真下のブロックで決まる）
     */
    static Component format(Note note, Instrument instrument) {
        int id = note.getId() & 0xFF; // byte → 0〜24
        String head = noteHead(note);                  // GOLD
        String solfege = solfegePart(note);            // WHITE
        String tuning = tuningSegment(id);             // GRAY
        String instrumentText = instrumentSegment(instrument); // AQUA

        // ドレミ成分の末尾を詰めて「♪ 音名 (ドレミ)」全体を固定幅にする → 調律の開始位置が固定。
        String solfegePadded = FontWidth.padRight(solfege, COL_NOTE_WIDTH - FontWidth.width(head));
        // 調律列を固定幅にする → 楽器: の開始位置が固定。
        String tuningPadded = FontWidth.padRight(tuning, COL_TUNING_WIDTH);
        // 楽器列を固定幅にする → 行の総幅が一定 → 中央寄せが一定 → 行頭の ♪ も固定。
        String instrumentPadded = FontWidth.padRight(instrumentText, COL_INSTRUMENT_WIDTH);

        return Component.text(head, NamedTextColor.GOLD)
                .append(Component.text(solfegePadded, NamedTextColor.WHITE))
                .append(Component.text(tuningPadded, NamedTextColor.GRAY))
                .append(Component.text(instrumentPadded, NamedTextColor.AQUA));
    }

    // 各列のテキストはここだけで組み立てる。計測ループ（static 初期化）と format() の
    // 双方がこれらを呼ぶことで、区切りスペースや表記が一箇所に集約され、整列のずれを防ぐ。

    /** 「♪ 音名」（GOLD で表示する先頭部分）。 */
    private static String noteHead(Note note) {
        return "♪ " + scientificName(note);
    }

    /** 「 (ドレミ)」（WHITE で表示する部分）。 */
    private static String solfegePart(Note note) {
        return " (" + solfegeName(note) + ")";
    }

    /** 「  調律 n/24」（GRAY）。 */
    private static String tuningSegment(int id) {
        return "  調律 " + id + "/24";
    }

    /** 「  楽器: 名前」（AQUA）。 */
    private static String instrumentSegment(Instrument instrument) {
        return "  楽器: " + instrumentName(instrument);
    }

    /** 科学的音名（例: F#3, C4）。オクターブ番号は C で繰り上がるため id から算出する。 */
    private static String scientificName(Note note) {
        int id = note.getId() & 0xFF;
        int octave = 3 + (id + 6) / 12; // id 0(=F#3)〜5→3, 6(=C4)〜17→4, 18(=C5)〜24→5
        return letter(note) + (note.isSharped() ? "#" : "") + octave;
    }

    /** ドレミ表記（例: ファ#, ド）。 */
    private static String solfegeName(Note note) {
        String base = switch (note.getTone()) {
            case C -> "ド";
            case D -> "レ";
            case E -> "ミ";
            case F -> "ファ";
            case G -> "ソ";
            case A -> "ラ";
            case B -> "シ";
        };
        return base + (note.isSharped() ? "#" : "");
    }

    /** 音名のアルファベット表記。 */
    private static String letter(Note note) {
        return switch (note.getTone()) {
            case A -> "A";
            case B -> "B";
            case C -> "C";
            case D -> "D";
            case E -> "E";
            case F -> "F";
            case G -> "G";
        };
    }

    /**
     * 楽器名（日本語）。UI を日本語で統一し、英語フォールバックで列幅が広がるのを防ぐため、
     * 現行 enum の全楽器に日本語名を割り当てる。将来 enum に追加された未知の楽器のみ
     * {@link #prettify(String)} で enum 名を見やすい形へフォールバックする。
     * （頭ブロック系・トランペット系の訳は妥当な範囲の当て字。必要なら調整可。）
     */
    private static String instrumentName(Instrument instrument) {
        return switch (instrument) {
            case PIANO -> "ハープ";
            case BASS_DRUM -> "バスドラム";
            case SNARE_DRUM -> "スネアドラム";
            case STICKS -> "スティック";
            case BASS_GUITAR -> "ベース";
            case FLUTE -> "フルート";
            case BELL -> "ベル";
            case GUITAR -> "ギター";
            case CHIME -> "チャイム";
            case XYLOPHONE -> "シロフォン";
            case IRON_XYLOPHONE -> "アイアンシロフォン";
            case COW_BELL -> "カウベル";
            case DIDGERIDOO -> "ディジュリドゥ";
            case BIT -> "ビット";
            case BANJO -> "バンジョー";
            case PLING -> "プリング";
            case TRUMPET -> "トランペット";
            case TRUMPET_EXPOSED -> "露出トランペット";
            case TRUMPET_OXIDIZED -> "酸化トランペット";
            case TRUMPET_WEATHERED -> "風化トランペット";
            case ZOMBIE -> "ゾンビ";
            case SKELETON -> "スケルトン";
            case CREEPER -> "クリーパー";
            case DRAGON -> "ドラゴン";
            case WITHER_SKELETON -> "ウィザースケルトン";
            case PIGLIN -> "ピグリン";
            case CUSTOM_HEAD -> "カスタムヘッド";
            default -> prettify(instrument.name()); // 将来追加された未知の楽器のみ enum 名で表示
        };
    }

    private static String prettify(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
