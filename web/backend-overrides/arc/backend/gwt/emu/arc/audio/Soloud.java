package arc.audio;

/**
 * Browser super-source for Arc's JNI SoLoud bridge.
 *
 * The first web milestone intentionally runs muted. Keeping this class at the
 * exact current Arc package/API removes JNI from GWT's translation graph while
 * letting Mindustry's real Sound/Music call sites remain unchanged. A WebAudio
 * implementation can replace these method bodies later without changing game code.
 */
public class Soloud{
    static void init(){}
    static void deinit(){}
    static String backendString(){ return "Web (muted)"; }
    static int backendId(){ return 0; }
    static int backendChannels(){ return 0; }
    static int backendSamplerate(){ return 0; }
    static int backendBufferSize(){ return 0; }
    static int version(){ return 0; }
    static int activeVoiceCount(){ return 0; }
    static void stopAll(){}
    static void pauseAll(boolean paused){}

    static void biquadSet(long handle, int type, float frequency, float resonance){}
    static void echoSet(long handle, float delay, float decay, float filter){}
    static void lofiSet(long handle, float sampleRate, float bitDepth){}
    static void flangerSet(long handle, float delay, float frequency){}
    static void waveShaperSet(long handle, float amount){}
    static void bassBoostSet(long handle, float amount){}
    static void robotizeSet(long handle, float freq, int waveform){}
    static void freeverbSet(long handle, float mode, float roomSize, float damp, float width){}

    static long filterBiquad(){ return 0L; }
    static long filterEcho(){ return 0L; }
    static long filterLofi(){ return 0L; }
    static long filterFlanger(){ return 0L; }
    static long filterBassBoost(){ return 0L; }
    static long filterWaveShaper(){ return 0L; }
    static long filterRobotize(){ return 0L; }
    static long filterFreeverb(){ return 0L; }

    static void setGlobalFilter(int index, long handle){}
    static void filterFade(int voice, int filter, int attribute, float value, float timeSec){}
    static void filterSet(int voice, int filter, int attribute, float value){}

    static long busNew(){ return 0L; }

    static void idSeek(int id, float seconds){}
    static void idVolume(int id, float volume){}
    static float idGetVolume(int id){ return 0f; }
    static void idPan(int id, float pan){}
    static void idPitch(int id, float pitch){}
    static void idPause(int id, boolean pause){}
    static boolean idGetPause(int voice){ return false; }
    static void idProtected(int id, boolean protect){}
    static void idStop(int voice){}
    static void idLooping(int voice, boolean looping){}
    static boolean idGetLooping(int voice){ return false; }
    static float idPosition(int voice){ return 0f; }
    static boolean idValid(int voice){ return false; }

    static long wavLoadBytes(byte[] bytes, int length){ return 0L; }
    static long wavLoadFile(String path){ return 0L; }
    static long streamLoadBytes(byte[] bytes, int length){ return 0L; }
    static long streamLoadFile(String path){ return 0L; }
    static double streamLength(long handle){ return 0d; }
    static double wavLength(long handle){ return 0d; }

    static void sourceDestroy(long handle){}
    static void sourceInaudible(long handle, boolean tick, boolean play){}
    static int sourcePlay(long handle){ return -1; }
    static int sourceCount(long handle){ return 0; }
    static int sourcePlay(long handle, float volume, float pitch, float pan, boolean loop){ return -1; }
    static int sourcePlayBus(long handle, long busHandle, float volume, float pitch, float pan, boolean loop){ return -1; }
    static void sourcePriority(long handle, float priority){}
    static void sourceMinConcurrentInterrupt(long handle, float value){}
    static void sourceMaxConcurrent(long handle, int maxConcurrent){}
    static void sourceConcurrentGroup(long handle, int group){}
    static void sourceLoop(long handle, boolean loop){}
    static void sourceSingleInstance(long handle, boolean single){}
    static void sourceStop(long handle){}
    static void sourceFilter(long handle, int index, long filter){}

    static int pauseDevice(){ return 0; }
    static int resumeDevice(){ return 0; }
}
