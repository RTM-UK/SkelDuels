package me.skeletica.duels.stats;

public class PlayerStats {
    private int wins, losses, kills, deaths, bestStreak, streak;
    public int wins(){return wins;} public int losses(){return losses;} public int kills(){return kills;} public int deaths(){return deaths;} public int bestStreak(){return bestStreak;} public int streak(){return streak;}
    public void win(){wins++;streak++;if(streak>bestStreak)bestStreak=streak;} public void loss(){losses++;streak=0;} public void kill(){kills++;} public void death(){deaths++;}
    public double winRate(){return wins+losses==0?0:(wins*100.0)/(wins+losses);}
    public void set(int w,int l,int k,int d,int s,int bs){wins=w;losses=l;kills=k;deaths=d;streak=s;bestStreak=bs;}
}
