import TSim.*;
import java.util.HashMap;
import java.util.Stack;
import java.util.concurrent.Semaphore;

public class Lab1 {

  public Lab1(int speed1, int speed2) {
    // Shared fair binary semaphores for track SECTIONS 1..5
    HashMap<Integer, Semaphore> sems = new HashMap<>();
    for (int i = 1; i <= 5; i++) {
      sems.put(i, new Semaphore(1, true));
    }

    // Station platform occupancy (binary) — 4 total
    StationLocks stationLocks = new StationLocks();

    Train t1 = new Train(1, speed1, Direction.DOWN, sems, stationLocks);
    Train t2 = new Train(2, speed2, Direction.UP, sems, stationLocks);

    new Thread(t1).start();
    new Thread(t2).start();
  }

  enum Direction {
    UP, DOWN
  }

  // Which platform we currently hold (to release on departure)
  enum PlatformHeld {
    TOP_A_16_3, TOP_B_16_5, BOT_A_16_11, BOT_B_16_13, NONE
  }

  static final class StationLocks {
    // TOP station platforms at x=16: y=3 (A default), y=5 (B)
    final Semaphore topA_16_3 = new Semaphore(1, true);
    final Semaphore topB_16_5 = new Semaphore(1, true);
    // BOTTOM station platforms at x=16: y=11 (A default), y=13 (B)
    final Semaphore botA_16_11 = new Semaphore(1, true);
    final Semaphore botB_16_13 = new Semaphore(1, true);
  }

  public class Train implements Runnable {
    private final int id;
    private int speed; // signed; flips at stations
    private Direction dir;
    private final HashMap<Integer, Semaphore> sems; // section locks (1..5)
    private final StationLocks st; // platform locks
    private final TSimInterface tsi = TSimInterface.getInstance();

    // remember which middle lane (3 or 4) we took, so we can release correctly
    private final Stack<Integer> laneStack = new Stack<>();
    private PlatformHeld held = PlatformHeld.NONE;

    public Train(int id, int speed, Direction dir, HashMap<Integer, Semaphore> sems, StationLocks st) {
      this.id = id;
      this.speed = speed;
      this.dir = dir;
      this.sems = sems;
      this.st = st;
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

          // ===== SECTION 1 (left vertical connector) =====
          if (x == 6 && y == 6) {
            stop();
            if (dir == Direction.DOWN)
              acquire(1);
            else
              release(1);
            go();
            continue;
          } else if (x == 8 && y == 6) {
            stop();
            if (dir == Direction.DOWN)
              acquire(1);
            else
              release(1);
            go();
            continue;
          } else if (x == 10 && y == 7) {
            stop();
            if (dir == Direction.UP)
              acquire(1);
            else
              release(1);
            go();
            continue;
          } else if (x == 10 && y == 8) {
            stop();
            if (dir == Direction.UP)
              acquire(1);
            else
              release(1);
            go();
            continue;
          }

          // ===== SECTION 2 (right merge/split) =====
          else if (x == 14 && (y == 7 || y == 8)) {
            stop();
            if (dir == Direction.DOWN) {
              acquire(2);
              setSwitch(17, 7, (y == 7 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            } else {
              release(2);
            }
            go();
            continue;
          } else if (x == 12 && (y == 9 || y == 10)) {
            stop();
            if (dir == Direction.UP) {
              acquire(2);
              setSwitch(15, 9, (y == 9 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            } else {
              release(2);
            }
            go();
            continue;
          }

          // ===== SECTIONS 3 & 4 (middle parallel lanes) =====
          else if (x == 17 && y == 9) {
            stop();
            if (dir == Direction.DOWN) {
              if (tryAcquire(3)) {
                laneStack.push(3);
                setSwitch(15, 9, TSimInterface.SWITCH_RIGHT);
              } else {
                acquire(4);
                laneStack.push(4);
                setSwitch(15, 9, TSimInterface.SWITCH_LEFT);
              }
            } else if (dir == Direction.UP) {
              releaseLaneIfHeld();
            }
            go();
            continue;
          } else if (x == 1 && y == 10) {
            stop();
            if (dir == Direction.UP) {
              if (tryAcquire(3)) {
                laneStack.push(3);
                setSwitch(4, 9, TSimInterface.SWITCH_LEFT);
              } else {
                acquire(4);
                laneStack.push(4);
                setSwitch(4, 9, TSimInterface.SWITCH_RIGHT);
              }
            } else { // DOWN
              releaseLaneIfHeld();

              // Reserve a bottom-station platform
              if (st.botA_16_11.tryAcquire()) {
                held = PlatformHeld.BOT_A_16_11;
                setSwitch(3, 11, TSimInterface.SWITCH_RIGHT);
              } else {
                st.botB_16_13.acquire();
                held = PlatformHeld.BOT_B_16_13;
                setSwitch(3, 11, TSimInterface.SWITCH_LEFT);
              }
            }
            go();
            continue;
          }

          // ===== SECTION 5 (left junction) =====
          else if (x == 6 && (y == 9 || y == 10)) {
            stop();
            if (dir == Direction.DOWN) {
              acquire(5);
              setSwitch(4, 9, (y == 9 ? TSimInterface.SWITCH_LEFT : TSimInterface.SWITCH_RIGHT));
            } else {
              release(5);
            }
            go();
            continue;
          } else if (x == 5 && (y == 11 || y == 13)) {
            stop();
            if (dir == Direction.UP) {
              acquire(5);
              setSwitch(3, 11, (y == 11 ? TSimInterface.SWITCH_LEFT : TSimInterface.SWITCH_RIGHT));
            } else {
              release(5);
            }
            go();
            continue;
          }

          // ===== TOP station approach at (19,8) =====
          else if (x == 19 && y == 8 && dir == Direction.UP) {
            stop();
            if (st.topA_16_3.tryAcquire()) {
              held = PlatformHeld.TOP_A_16_3;
              setSwitch(17, 7, TSimInterface.SWITCH_LEFT); // to y=3
            } else {
              st.topB_16_5.acquire();
              held = PlatformHeld.TOP_B_16_5;
              setSwitch(17, 7, TSimInterface.SWITCH_RIGHT); // to y=5
            }
            go();
            continue;
          }

          // ===== STATIONS =====
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

          tsi.setSpeed(id, speed); // default keep rolling
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

    private boolean tryAcquire(int secId) {
      Semaphore s = sems.get(secId);
      return s != null && s.tryAcquire();
    }

    private void acquire(int secId) throws InterruptedException {
      Semaphore s = sems.get(secId);
      if (s != null)
        s.acquire();
    }

    private void release(int secId) {
      Semaphore s = sems.get(secId);
      if (s != null)
        s.release();
    }

    private void releaseLaneIfHeld() {
      if (!laneStack.isEmpty()) {
        int secId = laneStack.pop();
        release(secId);
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
