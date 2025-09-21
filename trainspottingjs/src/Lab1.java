import TSim.*;
import java.util.HashMap;
import java.util.Stack;
import java.util.concurrent.Semaphore;

public class Lab1 {

  public Lab1(int speed1, int speed2) {
    // Shared fair binary semaphores (sections 1..6) for both trains
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
    private final Stack<Integer> laneStack = new Stack<>(); // remembers chosen lane (3 or 4)

    public Train(int id, int speed, Direction dir, HashMap<Integer, Semaphore> semaphores) {
      this.id = id;
      this.speed = speed;
      this.dir = dir;
      this.sems = semaphores;
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

          // ===== Section 2: right-side funnel near switches (17,7) and (15,9) =====
          // Entering from right going DOWN at (14,7|8): acquire(2) and set (17,7) by
          // lane.
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
          }
          // Entering from right going UP at (12,9|10): acquire(2) and set (15,9) by lane.
          else if (x == 12 && (y == 9 || y == 10)) {
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

          // ===== Sections 3 & 4: parallel middle lanes (overtake zone) =====
          // At (17,9) going DOWN: prefer lane 3 (tryAcquire), else block for lane 4.
          else if (x == 17 && y == 9) {
            stop();
            if (dir == Direction.DOWN) {
              if (sems.get(3).tryAcquire()) {
                laneStack.push(3);
                setSwitch(15, 9, TSimInterface.SWITCH_RIGHT); // route to lane 3
              } else {
                sems.get(4).acquire();
                laneStack.push(4);
                setSwitch(15, 9, TSimInterface.SWITCH_LEFT); // route to lane 4
              }
            } else if (dir == Direction.UP) {
              // Exiting the split in the opposite direction: release whichever lane was
              // taken.
              releaseLaneIfHeld();
            }
            go();
            continue;
          }

          // At (1,10) going UP: prefer lane 3 (tryAcquire), else block for lane 4.
          // At (1,10) going DOWN: release whichever lane was taken earlier.
          else if (x == 1 && y == 10) {
            stop();
            if (dir == Direction.UP) {
              if (sems.get(3).tryAcquire()) {
                laneStack.push(3);
                setSwitch(4, 9, TSimInterface.SWITCH_LEFT); // route to lane 3
              } else {
                sems.get(4).acquire();
                laneStack.push(4);
                setSwitch(4, 9, TSimInterface.SWITCH_RIGHT); // route to lane 4
              }
            } else { // dir == DOWN
              releaseLaneIfHeld();
              // Note: switch (3,11) is set elsewhere when entering Section 5 from left.
            }
            go();
            continue;
          }

          // ===== Section 5: left throat near (4,9) and (3,11) =====
          // DOWN at (6,9|10): acquire(5) and set (4,9) by lane.
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
          // UP at (5,11|13): acquire(5) and set (3,11) by lane.
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

          // ===== Optional switch prep near station throats (map-dependent) =====
          else if (x == 14 && (y == 11 || y == 13) && dir == Direction.UP) {
            stop();
            setSwitch(15, 11, (y == 11 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            go();
            continue;
          } else if (x == 14 && (y == 3 || y == 5) && dir == Direction.DOWN) {
            stop();
            setSwitch(15, 3, (y == 3 ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT));
            go();
            continue;
          }

          // ===== Stations: stop, wait, reverse direction =====
          else if (x == 16 && (y == 13 || y == 11 || y == 5 || y == 3)) {
            stop();
            Thread.sleep(1000 + 20 * Math.abs(speed)); // 1–2 s wait rule
            dir = (dir == Direction.UP ? Direction.DOWN : Direction.UP);
            speed = -speed; // reverse direction
            tsi.setSpeed(id, speed);
            continue;
          }

          // Default action: keep speed unchanged.
          tsi.setSpeed(id, speed);
        }

      } catch (CommandException | InterruptedException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    // Helpers
    private void stop() throws CommandException {
      tsi.setSpeed(id, 0);
    }

    private void go() throws CommandException {
      tsi.setSpeed(id, speed);
    }

    private void acquire(int sec) throws InterruptedException {
      sems.get(sec).acquire();
    }

    private void release(int sec) {
      try {
        sems.get(sec).release();
      } catch (Exception ignored) {
      }
    }

    private void releaseLaneIfHeld() {
      if (!laneStack.isEmpty()) {
        int s = laneStack.pop();
        sems.get(s).release();
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
