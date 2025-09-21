import TSim.*;

import java.util.HashMap;
import java.util.Stack;
import java.util.concurrent.Semaphore;

public class Lab1 {

  public Lab1(int speed1, int speed2) {

    // Shared map: sectionId -> binary semaphore (1 permit)
    // Both train threads receive the SAME map so they coordinate via these
    // semaphores.
    HashMap<Integer, Semaphore> semaphores = new HashMap<Integer, Semaphore>();

    // Create two train controllers.
    // Train 1 starts moving "DOWN" (towards bottom/right station).
    // Train 2 starts moving "UP" (towards top/right station).
    Train one = new Train(1, speed1, Direction.DOWN, semaphores);
    Train two = new Train(2, speed2, Direction.UP, semaphores);

    // Start exactly two threads (one per train) as required by the lab.
    Thread threadOne = new Thread(one);
    Thread threadTwo = new Thread(two);

    threadOne.start();
    threadTwo.start();
  }

  // Simple direction enum: used to decide which sensor acts as ENTRY vs EXIT.
  enum Direction {
    UP,
    DOWN
  }

  public class Train implements Runnable {
    private int id; // 1 or 2
    private int speed; // signed speed (he flips sign when reversing)
    private TSimInterface tsi = TSimInterface.getInstance();
    private Direction dir; // current logical direction (UP/DOWN)
    private HashMap<Integer, Semaphore> semaphores; // shared section locks

    // Used to remember which middle lane (section 3 or 4) the train chose,
    // so it can release the right one on the way out.
    private Stack<Integer> trackStack = new Stack<>();

    public Train(int id, int speed, Direction dir, HashMap<Integer, Semaphore> semaphores) {
      this.id = id;
      this.speed = speed;
      this.dir = dir;
      this.semaphores = semaphores;
      initSemaphores(); // ensure all section semaphores exist (1..6)
    }

    int getSpeed() {
      return speed;
    }

    int getID() {
      return id;
    }

    @Override
    public void run() {
      try {
        // Start the train at its initial speed.
        tsi.setSpeed(id, speed);

        // Main control loop: block on sensors for THIS train only.
        while (true) {
          SensorEvent event = tsi.getSensor(id);

          // Only act on ACTIVE events (entering a sensor tile).
          if (event.getStatus() == SensorEvent.ACTIVE) {

            // =========================
            // SECTION 1 (left vertical connector, top end)
            // Sensors: (6,6), (8,6), (10,7), (10,8)
            // Enter/release depends on current direction.
            // =========================
            if (event.getXpos() == 6 && event.getYpos() == 6) {
              // Be safe: stop while acquiring / releasing to reduce timing risks.
              tsi.setSpeed(id, 0);
              if (dir.equals(Direction.DOWN)) {

                acquireTrack(1); // entering section 1 going DOWN
              } else {
                releaseTrack(1); // exiting section 1 going UP
              }
              tsi.setSpeed(id, speed);

            } else if (event.getXpos() == 8 && event.getYpos() == 6) {
              tsi.setSpeed(id, 0);
              if (dir.equals(Direction.DOWN)) {
                acquireTrack(1);
              } else {
                releaseTrack(1);
              }
              tsi.setSpeed(id, speed);

            } else if (event.getXpos() == 10 && event.getYpos() == 7) {
              tsi.setSpeed(id, 0);
              if (dir.equals(Direction.UP)) {
                acquireTrack(1); // entering section 1 going UP
              } else {
                releaseTrack(1); // exiting section 1 going DOWN
              }
              tsi.setSpeed(id, speed);

            } else if (event.getXpos() == 10 && event.getYpos() == 8) {
              tsi.setSpeed(id, 0);
              if (dir.equals(Direction.UP)) {
                acquireTrack(1);
              } else {
                releaseTrack(1);
              }
              tsi.setSpeed(id, speed);
            }

            // =========================
            // SECTION 2 (middle-right merge by switch (17,7) and near (15,9))
            // He also sets the switch based on which sensor lane you’re on.
            // Sensors (DOWN): (14,7) or (14,8) -> acquire(2) and set (17,7).
            // Sensors (UP) : (12,10) or (12,9) -> acquire(2) and set (15,9).
            // Opposite-direction sensors release(2).
            // =========================
            else if (event.getXpos() == 14 && (event.getYpos() == 7 || event.getYpos() == 8)) {
              tsi.setSpeed(id, 0);
              if (dir.equals(Direction.DOWN)) {
                acquireTrack(2); // entering section 2 when going DOWN

                // Decide branch at switch (17,7) depending on which lane (y=7 or y=8) you’re
                // on.
                // NOTE: He uses 1/2 literals:
                // 1 == SWITCH_LEFT, 2 == SWITCH_RIGHT (verify in your TSimInterface)
                if (event.getXpos() == 14 && event.getYpos() == 7) {
                  tsi.setSwitch(17, 7, 2); // RIGHT
                } else {
                  tsi.setSwitch(17, 7, 1); // LEFT
                }
              } else {
                releaseTrack(2); // exiting section 2 when going UP
              }
              tsi.setSpeed(id, speed);

            } else if (event.getXpos() == 12 && (event.getYpos() == 10 || event.getYpos() == 9)) {
              tsi.setSpeed(id, 0);
              if (dir.equals(Direction.UP)) {
                acquireTrack(2); // entering section 2 when going UP

                // Set the other switch (near 15,9) depending on lane (y=9 vs y=10).
                if (event.getXpos() == 12 && event.getYpos() == 9) {
                  tsi.setSwitch(15, 9, 2); // RIGHT
                } else {
                  tsi.setSwitch(15, 9, 1); // LEFT
                }
              } else {
                releaseTrack(2); // exiting section 2 when going DOWN
              }
              tsi.setSpeed(id, speed);
            }

            // =========================
            // SECTIONS 3 & 4 (split the parallel middle into two overtaking lanes)
            // He chooses lane 3 if it's free (tryAcquire), otherwise lane 4 (blocking
            // acquire).
            // He remembers chosen lane on a stack to release correctly later.
            // =========================
            else if (event.getXpos() == 17 && event.getYpos() == 9) {
              // At (17,9) DOWN direction: choose lane into left side via switch (15,9)
              tsi.setSpeed(id, 0);
              if (dir.equals(Direction.DOWN)) {
                if (isFreeForUse(3)) { // tryAcquire lane 3 non-blocking
                  trackStack.add(3); // remember we took section 3
                  tsi.setSwitch(15, 9, 2); // route to lane 3 (RIGHT)
                } else {
                  acquireTrack(4); // block on section 4
                  tsi.setSwitch(15, 9, 1); // route to lane 4 (LEFT)
                }
              } else if (dir.equals(Direction.UP)) {
                // On the way back UP: release whatever lane we had taken before.
                releasStackedTrack();
              }
              tsi.setSpeed(id, speed);

            } else if (event.getXpos() == 1 && event.getYpos() == 10) {
              // At (1,10) UP direction: choose lane back via switch (4,9)
              tsi.setSpeed(id, 0);
              if (dir.equals(Direction.UP)) {
                if (isFreeForUse(3)) {
                  trackStack.add(3);
                  tsi.setSwitch(4, 9, 1); // route to lane 3 (LEFT)
                } else {
                  acquireTrack(4);
                  tsi.setSwitch(4, 9, 2); // route to lane 4 (RIGHT)
                }
              } else if (dir.equals(Direction.DOWN)) {
                // On the way DOWN: leaving the split → release whichever we had taken
                releasStackedTrack();

                // Small tweak: set a far-left switch differently depending on train id.
                // (This violates the “don’t depend on train id for behavior” spirit,
                // but he only does it for an off-main-area switch; you should avoid this.)
                if (getID() == 2) {
                  tsi.setSwitch(3, 11, 2);
                } else if (getID() == 1) {
                  tsi.setSwitch(3, 11, 1);
                }
              }
              tsi.setSpeed(id, speed);

              // =========================
              // SECTION 5 (left/top region around (6,9)/(6,10) and switches at (4,9) and
              // (3,11))
              // Similar pattern: acquire on ENTRY depending on direction/lane, set switch
              // accordingly.
              // =========================
            } else if (event.getXpos() == 6 && (event.getYpos() == 9 || event.getYpos() == 10)) {
              tsi.setSpeed(id, 0);
              if (dir.equals(Direction.DOWN)) {
                acquireTrack(5);
                if (event.getYpos() == 9) {
                  tsi.setSwitch(4, 9, 1); // LEFT
                } else {
                  tsi.setSwitch(4, 9, 2); // RIGHT
                }
              } else {
                releaseTrack(5);
              }
              tsi.setSpeed(id, speed);

              // A one-off switch set when going UP past (19,8): choose (17,7) depending on
              // train id.
              // (Again, data-dependent on id — better to avoid in your own solution.)
            } else if (event.getXpos() == 19 && event.getYpos() == 8 && dir.equals(Direction.UP)) {
              tsi.setSpeed(id, 0);
              if (getID() == 1) {
                tsi.setSwitch(17, 7, 2); // RIGHT
              } else if (getID() == 2) {
                tsi.setSwitch(17, 7, 1); // LEFT
              }
              tsi.setSpeed(id, speed);

              // More of SECTION 5 on far left near (5,11)/(5,13) and switch (3,11)
            } else if (event.getXpos() == 5 && (event.getYpos() == 11 || event.getYpos() == 13)) {
              tsi.setSpeed(id, 0);
              if (dir == Direction.UP) {
                acquireTrack(5);
                if (event.getYpos() == 11) {
                  tsi.setSwitch(3, 11, 1); // LEFT
                } else {
                  tsi.setSwitch(3, 11, 2); // RIGHT
                }
              } else {
                releaseTrack(5);
              }
              tsi.setSpeed(id, speed);

              // =========================
              // STATIONS (turnaround)
              // He detects station approach by generic sensors on x=16 and some y’s:
              // (16,13) top, (16,5)/(16,3) bottom, (16,11) top alt.
              // On hit: stop, wait ~1–2s, flip direction, invert speed sign, resume.
              // =========================
            } else if (event.getXpos() == 16
                && (event.getYpos() == 13 || event.getYpos() == 5 || event.getYpos() == 3 || event.getYpos() == 11)) {
              tsi.setSpeed(id, 0);
              // Wait according to the lab rule (no randomness)
              Thread.sleep(1000 + (20 * Math.abs(speed)));
              // Flip logical direction and invert speed sign (must be stopped first)
              if (this.dir == Direction.UP) {
                this.dir = Direction.DOWN;
              } else {
                this.dir = Direction.UP;
              }
              this.speed *= -1;
              tsi.setSpeed(id, speed);
            }
          }
        }
      } catch (CommandException | InterruptedException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    // =========================
    // Semaphore wiring (sections 1..6)
    // =========================

    public void initSemaphores() {
      // Prepare 6 binary semaphores, each fair (FIFO). Only one train per section.
      putSemaphore(1);
      putSemaphore(2);
      putSemaphore(3);
      putSemaphore(4);
      putSemaphore(5);
      putSemaphore(6);
    }

    public void putSemaphore(int x) {
      Semaphore sem = new Semaphore(1, true); // fair binary semaphore
      semaphores.put(x, sem);
    }

    // Non-blocking check: if section is free, take it immediately (used to prefer
    // lane 3 over 4).
    public boolean isFreeForUse(int x) {
      Semaphore sem = semaphores.get(x);
      if (sem != null) {
        return sem.tryAcquire(); // returns true if we acquired; false if not available
      }
      return false;
    }

    // Blocking acquire: wait until the section becomes free.
    public void acquireTrack(int x) throws InterruptedException {
      Semaphore sem = semaphores.get(x);
      if (sem != null) {
        System.out.println("Train " + getID() + " is : Waiting for Acquairing: " + x);
        sem.acquire(); // block until available (mutual exclusion)
        if (x == 3 || x == 4) {
          trackStack.push(x); // remember which middle lane we took
        }
        System.out.println("Successfully Acquired " + x);
      }
    }

    // Release a section when done with it.
    public void releaseTrack(int x) {
      Semaphore sem = semaphores.get(x);
      if (sem != null) {
        System.out.println("Train " + getID() + " is : Releasing track: " + x);
        sem.release();
      }
    }

    // Pop and release whichever lane (3 or 4) we had taken earlier.
    public void releasStackedTrack() {
      if (!trackStack.empty()) {
        int topValue = trackStack.pop();
        Semaphore sem = semaphores.get(topValue);
        if (sem != null) {
          System.out.println("Train " + getID() + " is : Releasing track: " + topValue);
          sem.release();
        }
      }
    }
  }
}
