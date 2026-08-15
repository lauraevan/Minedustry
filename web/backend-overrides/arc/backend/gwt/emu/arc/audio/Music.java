package arc.audio;

import arc.files.*;
import arc.util.*;

/** Browser-safe silent Music implementation for the first boot milestone. */
public class Music extends AudioSource{
    public @Nullable Fi file;
    boolean looping;
    float volume = 1f, pitch = 1f, pan = 0f;
    float position;

    public static Music create(Fi file){
        Music music = new Music();
        music.file = file;
        return music;
    }

    public Music(Fi file) throws Exception{
        load(file);
    }

    public Music(){
    }

    public void load(byte[] bytes) throws Exception{
        handle = 0;
    }

    public void load(Fi file) throws Exception{
        this.file = file;
        handle = 0;
    }

    public void play(){
    }

    public void pause(boolean pause){
    }

    @Override
    public void stop(){
        position = 0f;
    }

    public boolean isPlaying(){
        return false;
    }

    public boolean isLooping(){
        return looping;
    }

    public void setLooping(boolean isLooping){
        looping = isLooping;
    }

    public float getVolume(){
        return volume;
    }

    public void setVolume(float volume){
        this.volume = volume;
    }

    public void set(float pan, float volume){
        this.pan = pan;
        this.volume = volume;
    }

    public float getPosition(){
        return position;
    }

    public void setPosition(float position){
        this.position = position;
    }

    @Override
    public float getLength(){
        return 0f;
    }

    @Override
    public String toString(){
        return "WebMusic: " + file;
    }
}
