import TSim.*;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class Lab1 {

  public Lab1(int speed1, int speed2) {
    // Shared fair binary semaphores (sections 1..5) for both trains
    HashMap<Integer, Semaphore> sems = new HashMap<>();
    for (int i = 1; i <= 5; i++)
      sems.put(i, new Semaphore(1, true));

    // Station occupancy semaphores (two per station)
    StationLocks stationLocks = new StationLocks();

    Train t1 = new Train(1, speed1, Direction.DOWN, sems, stationLocks);
    Train t2 = new Train(2, speed2, Direction.UP, sems, stationLocks);
    new Thread(t1).start();
    new Thread(t2).start();
  }

  enum Direction {
    UP, DOWN
  }

  // Which platform we currently hold (so we can release on departure)
  enum PlatformHeld {
    TOP_A_16_3, TOP_B_16_5, BOT_A_16_11, BOT_B_16_13, NONE
  }

  static final class StationLocks {
    // TOP station (x=16, y=3 and y=5)
    final Semaphore topA_16_3 = new Semaphore(1, true); // default
    final Semaphore topB_16_5 = new Semaphore(1, true); // alternate
    // BOTTOM station (x=16, y=11 and y=13)
    final Semaphore botA_16_11 = new Semaphore(1, true); // default
    final Semaphore botB_16_13 = new Semaphore(1, true); // alternate
  }

  public class Train implements Runnable {
    private final int id;
    private int speed; // signed; flips at stations
    private Direction dir;
    private final HashMap<Integer, Semaphore> sec; // section semaphores (1..5)
    private final StationLocks st; // station platform semaphores
    private final TSimInterface tsi = TSimInterface.getInstance();

    private PlatformHeld held = PlatformHeld.NONE;
    private int currentLane = 0; // 0 = none, 3 or 4 when middle-lane section held

    public Train(int id, int speed, Direction dir,
        HashMap<Integer, Semaphore> sections, StationLocks stationLocks) {
      this.id = id;
      this.speed = speed;
      this.dir = dir;
      this.sec = sections;
      this.st = stationLocks;
    }

    @Override
    public void run() {
      try {
        tsi.setSpeed(id, speed);

        while (true) {
          SensorEvent ev = tsi.getSensor(id);
          if (ev.getStatus() != SensorEvent.ACTIVE)
            continue;

          int x = ev.getXpos(), y = ev.getYpos();

          // ===== Section 1: left vertical connector =====
          if (x == 6 && y == 6) {
            stop();
            if (dir == Direction.DOWN)
              acquireSec(1);
            else
              releaseSec(1);
            go();
            continue;
          } else if (x == 8 && y == 6) {
            stop();
            if (dir == Direction.DOWN)
              acquireSec(1);
            else
              releaseSec(1);
            go();
            continue;
          } else if (x == 10 && y == 7) {
            stop();
            if (dir == Direction.UP)
              acquireSec(1);
            else
              releaseSec(1);
            go();
            continue;
          } else if (x == 10 && y == 8) {
            stop();
            if (dir == Direction.UP)
              acquireSec(1);
            else
              releaseSec(1);
            go();
            continue;
          }

          // ===== Section 2: right-side merge/split section (switches at (17,7) and
          // (15,9)) =====
          else if (x == 14 && (y == 7 || y == 8)) {
            stop();
            if (dir == Direction.DOWN) {
              acquireSec(2);
              setSwitch(17, 7, (y == 7 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            } else {
              releaseSec(2);
            }
            go();
            continue;
          } else if (x == 12 && (y == 9 || y == 10)) {
            stop();
            if (dir == Direction.UP) {
              acquireSec(2);
              setSwitch(15, 9, (y == 9 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            } else {
              releaseSec(2);
            }
            go();
            continue;
          }

          // ===== Sections 3 & 4: middle parallel lanes (overtake) =====
          else if (x == 17 && y == 9) {
            stop();
            if (dir == Direction.DOWN) {
              if (sec.get(3).tryAcquire()) {
                currentLane = 3;
                setSwitch(15, 9, TSimInterface.SWITCH_RIGHT);
              } else {
                sec.get(4).acquire();
                currentLane = 4;
                setSwitch(15, 9, TSimInterface.SWITCH_LEFT);
              }
            } else if (dir == Direction.UP) {
              releaseCurrentLane();
            }
            go();
            continue;
          } else if (x == 1 && y == 10) {
            stop();
            if (dir == Direction.UP) {
              if (sec.get(3).tryAcquire()) {
                currentLane = 3;
                setSwitch(4, 9, TSimInterface.SWITCH_LEFT);
              } else {
                sec.get(4).acquire();
                currentLane = 4;
                setSwitch(4, 9, TSimInterface.SWITCH_RIGHT);
              }
            } else { // DOWN
              releaseCurrentLane();
            }
            go();
            continue;
          }

          // ===== Section 5: left junction (switches (4,9) and (3,11)) =====
          else if (x == 6 && (y == 9 || y == 10)) {
            stop();
            if (dir == Direction.DOWN) {
              acquireSec(5);
              setSwitch(4, 9, (y == 9 ? TSimInterface.SWITCH_LEFT : TSimInterface.SWITCH_RIGHT));
            } else {
              releaseSec(5);
            }
            go();
            continue;
          } else if (x == 5 && (y == 11 || y == 13)) {
            stop();
            if (dir == Direction.UP) {
              acquireSec(5);
              setSwitch(3, 11, (y == 11 ? TSimInterface.SWITCH_LEFT : TSimInterface.SWITCH_RIGHT));
            } else {
              releaseSec(5);
            }
            go();
            continue;
          }

          // ===== Station approach (choose & reserve platform before entering) =====
          // TOP station approach (from right) when going UP
          else if (x == 19 && y == 8 && dir == Direction.UP) {
            stop();
            // default topA (16,3), else topB (16,5)
            if (st.topA_16_3.tryAcquire()) {
              held = PlatformHeld.TOP_A_16_3;
              // route to platform via appropriate switch setting(s)
              setSwitch(17, 7, TSimInterface.SWITCH_LEFT);
            } else {
              st.topB_16_5.acquire();
              held = PlatformHeld.TOP_B_16_5;
              setSwitch(17, 7, TSimInterface.SWITCH_RIGHT); // example; adjust if needed
            }
            go();
            continue;
          }

          // BOTTOM station approach (from left) when going DOWN
          else if (x == 1 && y == 10 && dir == Direction.DOWN) {
            stop();
            // default botA (16,11), else botB (16,13)
            if (st.botA_16_11.tryAcquire()) {
              held = PlatformHeld.BOT_A_16_11;
              setSwitch(3, 11, TSimInterface.SWITCH_RIGHT);
            } else {
              st.botB_16_13.acquire();
              held = PlatformHeld.BOT_B_16_13;
              setSwitch(3, 11, TSimInterface.SWITCH_LEFT);
            }
            go();
            continue;
          }

          // ===== Station platform sensors (park, reverse, then release held platform)
          // =====
          else if (x == 16 && (y == 13 || y == 11 || y == 5 || y == 3)) {
            stop();
            Thread.sleep(1000 + 20 * Math.abs(speed));
            dir = (dir == Direction.UP ? Direction.DOWN : Direction.UP);
            speed = -speed;

            switch (held) {
              case TOP_A_16_3 -> st.topA_16_3.release();
              case TOP_B_16_5 -> st.topB_16_5.release();
              case BOT_A_16_11 -> st.botA_16_11.release();
              case BOT_B_16_13 -> st.botB_16_13.release();
              case NONE -> {
              }
            }
            held = PlatformHeld.NONE;

            tsi.setSpeed(id, speed);
            continue;
          }

          // Default: keep rolling
          tsi.setSpeed(id, speed);
        }

      } catch (CommandException | InterruptedException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    // ---- helpers ----
    private void stop() throws CommandException {
      tsi.setSpeed(id, 0);
    }

    private void go() throws CommandException {
      tsi.setSpeed(id, speed);
    }

    private void acquireSec(int s) throws InterruptedException {
      sec.get(s).acquire();
    }

    private void releaseSec(int s) {
      try {
        sec.get(s).release();
      } catch (Exception ignored) {
      }
    }

    private void releaseCurrentLane() {
      if (currentLane == 3 || currentLane == 4) {
        sec.get(currentLane).release();
        currentLane = 0;
      }
    }

    private void setSwitch(int x, int y, int pos) {
      try {
        tsi.setSwitch(x, y, pos);
      } catch (CommandException e) {
        System.err.println("setSwitch(" + x + "," + y + "): " + e.getMessage());
      }
    }
  }
}
