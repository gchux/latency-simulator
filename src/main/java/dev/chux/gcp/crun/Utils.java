package dev.chux.gcp.crun;

public final class Utils {

  public static final int getLatency(int lower, int upper) {
    final int latency = (int) (Math.random()*(upper-lower))+lower;
    return (latency < 0)? -1*latency : latency;
  }

}
