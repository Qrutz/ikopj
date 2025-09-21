import TSim.*;
import java.util.HashMap;
import java.util.Stack;
import java.util.concurrent.Semaphore;

public class Lab1 {

  public Lab1(int speed1, int speed2) {
    // SHARED fair binary semaphores (1..6) for both trains
    HashMap<Integer, Semaphore> semaphores = new HashMap<>();
    for (int i = 1; i <= 6; i++)
      semaphores.put(i, new Semaphore(1, true));

    Train t1 = new Train(1, speed1, Direction.DOWN, semaphores);
    Train t2 = new Train(2, speed2, Direction.UP, semaphores);
    new Thread(t1).start();
    new Thread(t2).start();
  }

  enum Direction {
    UP, DOWN
  }

  public class Train implements Runnable {
    private final int id;
    private int speed; // signed; flips at stations
    private Direction dir;
    private final HashMap<Integer, Semaphore> sems;
    private final TSimInterface tsi = TSimInterface.getInstance();

    // which lane (S3/S4) we took; release later on opposite trigger
    private final Stack<Integer> laneStack = new Stack<>();

    public Train(int id, int speed, Direction dir, HashMap<Integer, Semaphore> semaphores) {
      this.id = id;
      this.speed = speed;
      this.dir = dir;
      this.sems = semaphores; // shared
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

          // ===== SECTION 1 (left vertical): (6,6)/(8,6) vs (10,7)/(10,8) =====
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

          // ===== SECTION 2 (right funnel) =====
          // DOWN entry at (14,7|8): acquire S2 + set (17,7) by y
          else if (x == 14 && (y == 7 || y == 8)) {
            stop();
            if (dir == Direction.DOWN) {
              acquire(2);
              // if y==7 → RIGHT, if y==8 → LEFT (swap if your map needs opposite)
              setSwitch(17, 7, (y == 7 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            } else {
              release(2);
            }
            go();
            continue;
          }
          // UP entry at (12,9|10): acquire S2 + set (15,9) by y
          else if (x == 12 && (y == 9 || y == 10)) {
            stop();
            if (dir == Direction.UP) {
              acquire(2);
              // y==9 → RIGHT (top lane), y==10 → LEFT (bottom lane)
              setSwitch(15, 9, (y == 9 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            } else {
              release(2);
            }
            go();
            continue;
          }

          // ===== SECTIONS 3 & 4 (middle parallel lanes) =====
          // At (17,9) going DOWN: choose lane (S3 tryAcquire else S4 acquire) and set
          // (15,9)
          else if (x == 17 && y == 9) {
            stop();
            if (dir == Direction.DOWN) {
              if (sems.get(3).tryAcquire()) {
                laneStack.push(3);
                setSwitch(15, 9, TSimInterface.SWITCH_RIGHT); // route to lane 3 (top)
                log("T" + id + " took lane S3");
              } else {
                sems.get(4).acquire();
                laneStack.push(4);
                setSwitch(15, 9, TSimInterface.SWITCH_LEFT); // route to lane 4 (bottom)
                log("T" + id + " took lane S4");
              }
            } else if (dir == Direction.UP) {
              // leaving the split in opposite direction
              releaseLaneIfHeld();
            }
            go();
            continue;
          }

          // At (1,10) going UP: choose lane back and set (4,9).
          // Going DOWN at (1,10): release whichever lane we had; also flip (3,11) by id
          // (per your mate).
          else if (x == 1 && y == 10) {
            stop();
            if (dir == Direction.UP) {
              if (sems.get(3).tryAcquire()) {
                laneStack.push(3);
                setSwitch(4, 9, TSimInterface.SWITCH_LEFT); // route to lane 3
                log("T" + id + " took lane S3");
              } else {
                sems.get(4).acquire();
                laneStack.push(4);
                setSwitch(4, 9, TSimInterface.SWITCH_RIGHT); // route to lane 4
                log("T" + id + " took lane S4");
              }
            } else { // dir == DOWN
              releaseLaneIfHeld();
              // ===== ID-dependent tweak (your mate did this; spec normally forbids) =====
              setSwitch(3, 11, (id == 2 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            }
            go();
            continue;
          }

          // ===== SECTION 5 (left/top throat) =====
          // DOWN entries: (6,9|10) → acquire(5) and set (4,9) by y
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
          }
          // UP entries: (5,11|13) → acquire(5) and set (3,11) by y
          else if (x == 5 && (y == 11 || y == 13)) {
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

          // ===== Extra: at (19,8) going UP, set (17,7) by id (your mate’s hack) =====
          else if (x == 19 && y == 8 && dir == Direction.UP) {
            stop();
            // id-dependent (hacky but included per your request)
            setSwitch(17, 7, (id == 1 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            go();
            continue;
          }

          // ===== STATIONS (turnaround) using your x==16 sensors =====
          else if (x == 16 && (y == 13 || y == 11 || y == 5 || y == 3)) {
            stop();
            Thread.sleep(1000 + 20 * Math.abs(speed)); // 1–2s rule
            dir = (dir == Direction.UP ? Direction.DOWN : Direction.UP);
            speed = -speed; // must be stopped before flipping sign
            tsi.setSpeed(id, speed);
            continue;
          }

          // nothing matched: keep rolling
          tsi.setSpeed(id, speed);
        }

      } catch (CommandException | InterruptedException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    // ---- tiny helpers ----
    private void stop() throws CommandException {
      tsi.setSpeed(id, 0);
    }

    private void go() throws CommandException {
      tsi.setSpeed(id, speed);
    }

    private void log(String s) {
      System.out.println(s);
    }

    private void acquire(int sec) throws InterruptedException {
      log("T" + id + " waiting S" + sec);
      sems.get(sec).acquire();
      log("T" + id + " acquired S" + sec);
    }

    private void release(int sec) {
      try {
        sems.get(sec).release();
        log("T" + id + " released S" + sec);
      } catch (Exception ignored) {
      }
    }

    private void releaseLaneIfHeld() {
      if (!laneStack.isEmpty()) {
        int s = laneStack.pop();
        sems.get(s).release();
        log("T" + id + " released lane S" + s);
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
