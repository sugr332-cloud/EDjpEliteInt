package elite.intel.ai.ears.parakeet;

import com.google.common.eventbus.Subscribe;
import elite.intel.eventbus.UiBus;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
import elite.intel.ui.event.AppLogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ParakeetSTTImplTest {

    private final EventRecorder eventRecorder = new EventRecorder();
    private Language originalLanguage;
    private Supplier<Path> originalSupplier;

    @BeforeEach
    void setUp() {
        originalLanguage = SystemSession.getInstance().getLanguage();
        originalSupplier = ParakeetSTTImpl.reazonSpeechModelDirSupplier;
        UiBus.register(eventRecorder);
    }

    @AfterEach
    void tearDown() {
        SystemSession.getInstance().setLanguage(originalLanguage);
        ParakeetSTTImpl.reazonSpeechModelDirSupplier = originalSupplier;
        UiBus.unregister(eventRecorder);
    }

    @Test
    void isJapaneseModelAvailableChecksAllRequiredFiles(@TempDir Path tempDir) throws Exception {
        assertFalse(ParakeetSTTImpl.isJapaneseModelAvailable(null));
        assertFalse(ParakeetSTTImpl.isJapaneseModelAvailable(tempDir.resolve("non_existent")));
        assertFalse(ParakeetSTTImpl.isJapaneseModelAvailable(tempDir));

        // Create 3 of the 4 files
        Files.createFile(tempDir.resolve("encoder-epoch-99-avg-1.int8.onnx"));
        Files.createFile(tempDir.resolve("decoder-epoch-99-avg-1.onnx"));
        Files.createFile(tempDir.resolve("joiner-epoch-99-avg-1.int8.onnx"));
        assertFalse(ParakeetSTTImpl.isJapaneseModelAvailable(tempDir), "Should be false when tokens.txt is missing");

        // Create the 4th file
        Files.createFile(tempDir.resolve("tokens.txt"));
        assertTrue(ParakeetSTTImpl.isJapaneseModelAvailable(tempDir), "Should be true when all 4 files are present");
    }

    @Test
    void startWithMissingJapaneseModelPublishesSystemMessageAndDoesNotStartCapture(@TempDir Path emptyDir) throws Exception {
        ParakeetSTTImpl.reazonSpeechModelDirSupplier = () -> emptyDir;
        SystemSession.getInstance().setLanguage(Language.JA);

        ParakeetSTTImpl stt = new ParakeetSTTImpl();
        try {
            stt.start();

            // Verify system message published
            assertTrue(eventRecorder.hasAppLogContaining("日本語音声認識モデルが見つかりません"),
                    "Expected missing model system message in UI log");

            // Verify audio capture thread was NOT started
            Field processingThreadField = ParakeetSTTImpl.class.getDeclaredField("processingThread");
            processingThreadField.setAccessible(true);
            assertNull(processingThreadField.get(stt), "Capture thread must not be started when model is missing");
        } finally {
            stt.stop();
        }
    }

    @Test
    void toLangCodeMapsLanguagesCorrectly() {
        assertEquals("ja", ParakeetSTTImpl.toLangCode(Language.JA));
        assertEquals("en", ParakeetSTTImpl.toLangCode(Language.EN));
        assertEquals("fr", ParakeetSTTImpl.toLangCode(Language.FR));
        assertEquals("de", ParakeetSTTImpl.toLangCode(Language.DE));
        assertEquals("es", ParakeetSTTImpl.toLangCode(Language.ES));
        assertEquals("ru", ParakeetSTTImpl.toLangCode(Language.RU));
        assertEquals("uk", ParakeetSTTImpl.toLangCode(Language.UK));
        assertEquals("it", ParakeetSTTImpl.toLangCode(Language.IT));
        assertEquals("pt", ParakeetSTTImpl.toLangCode(Language.PT));
        assertEquals("pt", ParakeetSTTImpl.toLangCode(Language.PTBZ));
    }

    @Test
    void resolveModelSpecBranchesCorrectlyBetweenJaAndOtherLanguages() {
        ParakeetSTTImpl.ModelSpec jaSpec = ParakeetSTTImpl.resolveModelSpec(Language.JA);
        assertEquals(80, jaSpec.featureDim());
        assertEquals("encoder-epoch-99-avg-1.int8.onnx", jaSpec.encoderFile().getFileName().toString());
        assertEquals("decoder-epoch-99-avg-1.onnx", jaSpec.decoderFile().getFileName().toString());
        assertEquals("joiner-epoch-99-avg-1.int8.onnx", jaSpec.joinerFile().getFileName().toString());
        assertEquals("tokens.txt", jaSpec.tokensFile().getFileName().toString());

        ParakeetSTTImpl.ModelSpec enSpec = ParakeetSTTImpl.resolveModelSpec(Language.EN);
        assertEquals(128, enSpec.featureDim());
        assertEquals("encoder.int8.onnx", enSpec.encoderFile().getFileName().toString());
        assertEquals("decoder.int8.onnx", enSpec.decoderFile().getFileName().toString());
        assertEquals("joiner.int8.onnx", enSpec.joinerFile().getFileName().toString());
        assertEquals("tokens.txt", enSpec.tokensFile().getFileName().toString());

        ParakeetSTTImpl.ModelSpec deSpec = ParakeetSTTImpl.resolveModelSpec(Language.DE);
        assertEquals(128, deSpec.featureDim());
        assertEquals("encoder.int8.onnx", deSpec.encoderFile().getFileName().toString());
    }

    @Test
    void sanitizeTranscriptPreservesCasingForJaAndLowercasesForOthers() {
        // Japanese: preserve uppercase/casing, trim whitespace
        assertEquals("フレームシフトドライブ", ParakeetSTTImpl.sanitizeTranscript("  フレームシフトドライブ  ", Language.JA));
        assertEquals("FSD ジャンプ", ParakeetSTTImpl.sanitizeTranscript("  FSD ジャンプ  ", Language.JA));

        // English: lowercase and trim
        assertEquals("deploy cargo scoop", ParakeetSTTImpl.sanitizeTranscript("  DEPLOY CARGO SCOOP  ", Language.EN));
        assertEquals("lights on", ParakeetSTTImpl.sanitizeTranscript("Lights On", Language.EN));

        // Other: lowercase and trim
        assertEquals("fahre fahrwerk aus", ParakeetSTTImpl.sanitizeTranscript("  FAHRE FAHRWERK AUS  ", Language.DE));

        // Null / empty
        assertEquals("", ParakeetSTTImpl.sanitizeTranscript(null, Language.JA));
        assertEquals("", ParakeetSTTImpl.sanitizeTranscript("   ", Language.JA));
    }

    @Test
    void isTranscriptUsableEnforcesMinLengthPerLanguage() {
        // Japanese: minLength = 2
        assertFalse(ParakeetSTTImpl.isTranscriptUsable(null, Language.JA));
        assertFalse(ParakeetSTTImpl.isTranscriptUsable("", Language.JA));
        assertFalse(ParakeetSTTImpl.isTranscriptUsable("   ", Language.JA));
        assertFalse(ParakeetSTTImpl.isTranscriptUsable("あ", Language.JA));
        assertTrue(ParakeetSTTImpl.isTranscriptUsable("発進", Language.JA));
        assertTrue(ParakeetSTTImpl.isTranscriptUsable("ギア", Language.JA));

        // English: minLength = 3 (matches legacy behavior)
        assertFalse(ParakeetSTTImpl.isTranscriptUsable(null, Language.EN));
        assertFalse(ParakeetSTTImpl.isTranscriptUsable("", Language.EN));
        assertFalse(ParakeetSTTImpl.isTranscriptUsable("   ", Language.EN));
        assertFalse(ParakeetSTTImpl.isTranscriptUsable("a", Language.EN));
        assertFalse(ParakeetSTTImpl.isTranscriptUsable("go", Language.EN));
        assertTrue(ParakeetSTTImpl.isTranscriptUsable("yes", Language.EN));
        assertTrue(ParakeetSTTImpl.isTranscriptUsable("dock", Language.EN));

        // Other: minLength = 3
        assertFalse(ParakeetSTTImpl.isTranscriptUsable("ja", Language.DE));
        assertTrue(ParakeetSTTImpl.isTranscriptUsable("aus", Language.DE));
    }

    @Test
    void hasSpeechEnergyDetectsSignalsAboveThreshold() {
        // Silent buffer (320 bytes = 1 frame of 16-bit mono 16kHz)
        byte[] silence = new byte[320];
        assertFalse(ParakeetSTTImpl.hasSpeechEnergy(silence, 50.0));

        // Buffer with high amplitude wave
        byte[] loud = new byte[320];
        for (int i = 0; i < loud.length; i += 2) {
            short sample = 1000;
            loud[i] = (byte) (sample & 0xFF);
            loud[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        assertTrue(ParakeetSTTImpl.hasSpeechEnergy(loud, 50.0));
        assertFalse(ParakeetSTTImpl.hasSpeechEnergy(loud, 2000.0));
    }

    @Test
    void calculateRMSComputesCorrectly() {
        assertEquals(0.0, ParakeetSTTImpl.calculateRMS(new byte[0], 0));
        assertEquals(0.0, ParakeetSTTImpl.calculateRMS(new byte[1], 1));

        byte[] silence = new byte[100];
        assertEquals(0.0, ParakeetSTTImpl.calculateRMS(silence, silence.length));

        byte[] constant = new byte[20];
        short val = 1000;
        for (int i = 0; i < constant.length; i += 2) {
            constant[i] = (byte) (val & 0xFF);
            constant[i + 1] = (byte) ((val >> 8) & 0xFF);
        }
        assertEquals(1000.0, ParakeetSTTImpl.calculateRMS(constant, constant.length), 0.01);
    }

    @Test
    void trimPreRollForJapaneseMaintainsAtLeast500msBeforeLatestFrameWith100msFrames() {
        Deque<byte[]> preRoll = new ArrayDeque<>();
        final int frameSize = 3200; // 100ms at 16kHz 16-bit mono

        for (int i = 0; i < 10; i++) {
            preRoll.addLast(new byte[frameSize]);
            ParakeetSTTImpl.trimPreRoll(preRoll, Language.JA);
        }

        // Prior frames: 16,000 / 3,200 = 5 frames (16,000 bytes)
        // Total frames: 5 prior + 1 latest = 6 frames
        assertEquals(6, preRoll.size());

        int priorBytes = calculatePriorBytes(preRoll);
        assertTrue(priorBytes >= ParakeetSTTImpl.JA_PRE_ROLL_PRIOR_BYTES);
        assertEquals(16000, priorBytes);

        // Verify minimality: removing first would violate >= 16,000
        byte[] first = preRoll.peekFirst();
        assertNotNull(first);
        assertTrue((priorBytes - first.length) < ParakeetSTTImpl.JA_PRE_ROLL_PRIOR_BYTES);
    }

    @Test
    void trimPreRollForJapaneseMaintainsAtLeast500msBeforeLatestFrameWith50msFrames() {
        Deque<byte[]> preRoll = new ArrayDeque<>();
        final int frameSize = 1600; // 50ms at 16kHz 16-bit mono

        for (int i = 0; i < 20; i++) {
            preRoll.addLast(new byte[frameSize]);
            ParakeetSTTImpl.trimPreRoll(preRoll, Language.JA);
        }

        // Prior frames: 16,000 / 1,600 = 10 frames (16,000 bytes)
        // Total frames: 10 prior + 1 latest = 11 frames
        assertEquals(11, preRoll.size());
        int priorBytes = calculatePriorBytes(preRoll);
        assertEquals(16000, priorBytes);

        byte[] first = preRoll.peekFirst();
        assertNotNull(first);
        assertTrue((priorBytes - first.length) < ParakeetSTTImpl.JA_PRE_ROLL_PRIOR_BYTES);
    }

    @Test
    void trimPreRollForJapaneseMaintainsAtLeast500msBeforeLatestFrameWith32msFrames() {
        Deque<byte[]> preRoll = new ArrayDeque<>();
        final int frameSize = 1024; // 32ms at 16kHz 16-bit mono

        for (int i = 0; i < 30; i++) {
            preRoll.addLast(new byte[frameSize]);
            ParakeetSTTImpl.trimPreRoll(preRoll, Language.JA);
        }

        // Prior frames: ceil(16,000 / 1,024) = 16 frames (16,384 bytes)
        // Total frames: 16 prior + 1 latest = 17 frames
        assertEquals(17, preRoll.size());
        int priorBytes = calculatePriorBytes(preRoll);
        assertEquals(16384, priorBytes);
        assertTrue(priorBytes >= ParakeetSTTImpl.JA_PRE_ROLL_PRIOR_BYTES);

        // Verify minimality
        byte[] first = preRoll.peekFirst();
        assertNotNull(first);
        assertTrue((priorBytes - first.length) < ParakeetSTTImpl.JA_PRE_ROLL_PRIOR_BYTES);
    }

    @Test
    void trimPreRollForJapaneseWithVaryingFrameLengthsMaintainsMinimality() {
        Deque<byte[]> preRoll = new ArrayDeque<>();
        int[] pattern = {3200, 800, 1600, 800, 3200, 1600, 800};

        for (int round = 0; round < 10; round++) {
            for (int len : pattern) {
                preRoll.addLast(new byte[len]);
                ParakeetSTTImpl.trimPreRoll(preRoll, Language.JA);

                int priorBytes = calculatePriorBytes(preRoll);
                if (preRoll.size() > 1 && priorBytes >= ParakeetSTTImpl.JA_PRE_ROLL_PRIOR_BYTES) {
                    byte[] first = preRoll.peekFirst();
                    assertNotNull(first);
                    assertTrue((priorBytes - first.length) < ParakeetSTTImpl.JA_PRE_ROLL_PRIOR_BYTES,
                            "Pre-roll must be the minimal set holding >= 16000 prior bytes");
                }
            }
        }

        int finalPriorBytes = calculatePriorBytes(preRoll);
        assertTrue(finalPriorBytes >= ParakeetSTTImpl.JA_PRE_ROLL_PRIOR_BYTES);
    }

    @Test
    void trimPreRollForEnglishKeepsExactlyTwoFrames() {
        Deque<byte[]> preRoll = new ArrayDeque<>();
        for (int i = 0; i < 10; i++) {
            preRoll.addLast(new byte[3200]);
            ParakeetSTTImpl.trimPreRoll(preRoll, Language.EN);
        }
        assertEquals(2, preRoll.size(), "English pre-roll must stay at exactly PRE_ROLL_FRAMES = 2");
    }

    @Test
    void pushToTalkDoesNotUsePreRoll() throws Exception {
        SystemSession.getInstance().setPushToTalkEnabled(true);
        try {
            ParakeetSTTImpl stt = new ParakeetSTTImpl();
            Field preRollField = ParakeetSTTImpl.class.getDeclaredField("preRoll");
            preRollField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Deque<byte[]> preRoll = (Deque<byte[]>) preRollField.get(stt);

            preRoll.addLast(new byte[1000]);
            preRoll.addLast(new byte[1000]);
            assertFalse(preRoll.isEmpty());

            preRoll.clear();
            assertTrue(preRoll.isEmpty(), "PTT mode must not retain pre-roll audio");
        } finally {
            SystemSession.getInstance().setPushToTalkEnabled(false);
        }
    }

    private static int calculatePriorBytes(Deque<byte[]> preRoll) {
        if (preRoll.size() <= 1) return 0;
        int total = 0;
        int count = 0;
        int priorLimit = preRoll.size() - 1;
        for (byte[] frame : preRoll) {
            if (count < priorLimit) {
                total += frame.length;
            }
            count++;
        }
        return total;
    }

    private static class EventRecorder {
        private final List<AppLogEvent> logEvents = new ArrayList<>();

        @Subscribe
        public void onAppLog(AppLogEvent event) {
            logEvents.add(event);
        }

        boolean hasAppLogContaining(String fragment) {
            return logEvents.stream()
                    .anyMatch(e -> e.getData() != null && e.getData().contains(fragment));
        }
    }
}
