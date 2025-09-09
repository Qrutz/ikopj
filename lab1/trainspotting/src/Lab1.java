import TSim.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class Lab1 {

  public Lab1(int speed1, int speed2) {
    TSimInterface tsi = TSimInterface.getInstance();

    Thread t1 = new Thread(new Train(1, speed1, tsi));
    Thread t2 = new Thread(new Train(2, speed2, tsi));

    try {
      tsi.setSpeed(1,speed1);
    }
    catch (CommandException e) {
      e.printStackTrace();    // or only e.getMessage() for the error
      System.exit(1);
    }
  }
}

class Train extends Thread{
  private int id;
  private int speed;
  private TSimInterface tsi;

  public Train(int id, int speed, TSimInterface tsi){
    this.id = id;
    this.speed = speed;
    this.tsi = tsi;
  }

  @Override
  public void run(){
    try{
      tsi.setSpeed(id, speed);

      while(true){
        SensorEvent se = tsi.getSensor(id);

        if(se.getStatus() == SensorEvent.ACTIVE){
          int x = se.getXpos();
          int y = se.getYpos();
        }
      }
    } catch(Exception e){
      e.printStackTrace();
      System.exit(1);
    }
  }
}

class Map{
  private static HashMap<Position, Section> sensorToSectionMapping = null;

  public static void init(){
    sensorToSectionMapping = new HashMap<>();
    Semaphore[] semaphores = new Semaphore[9];
    Position[] switches = new Position[4];
    Section[] section = new Section[4];
    Track[] tracks = new Track[16];

    for(int i = 0; i < semaphores.length; i++){
      semaphores[i] = new Semaphore(1);
    }

        switches[0] = new Position(3, 11);
        switches[1] = new Position(4, 9);
        switches[2] = new Position(15, 9);
        switches[3] = new Position(17, 7);

        section[0] = new Section(Direction.TowardsB, semaphores[0], new Position(6, 7));
        section[1] = new Section(Direction.TowardsB, semaphores[0], new Position(8, 5));
        section[2] = new Section(Direction.TowardsA, semaphores[0], new Position(10, 7));
        section[3] = new Section(Direction.TowardsA, semaphores[0], new Position(9, 8));

        tracks[0] = new Track(Direction.TowardsB, semaphores[1], new Position(5, 11));
        tracks[1] = new Track(Direction.TowardsB, semaphores[2], new Position(3, 13));
        tracks[2] = new Track(Direction.TowardsB, semaphores[3], new Position(2, 9));
        tracks[3] = new Track(Direction.TowardsB, semaphores[4], new Position(13, 9));
        tracks[4] = new Track(Direction.TowardsB, semaphores[5], new Position(13, 10));
        tracks[5] = new Track(Direction.TowardsB, semaphores[6], new Position(19, 7));
        tracks[6] = new Track(Direction.TowardsB, semaphores[7], new Position(16, 3));
        tracks[7] = new Track(Direction.TowardsB, semaphores[8], new Position(16, 5));

        tracks[8] = new Track(Direction.TowardsA, semaphores[1], new Position(16, 11));
        tracks[9] = new Track(Direction.TowardsA, semaphores[2], new Position(16, 13));
        tracks[10] = new Track(Direction.TowardsA, semaphores[3], new Position(1, 11));
        tracks[11] = new Track(Direction.TowardsA, semaphores[4], new Position(6, 9));
        tracks[12] = new Track(Direction.TowardsA, semaphores[5], new Position(6, 10));
        tracks[13] = new Track(Direction.TowardsA, semaphores[6], new Position(17, 9));
        tracks[14] = new Track(Direction.TowardsA, semaphores[7], new Position(15, 7));
        tracks[15] = new Track(Direction.TowardsA, semaphores[8], new Position(15, 8));

        tracks[0].addConnection(tracks[2], switches[0], TSimInterface.SWITCH_LEFT);
        tracks[1].addConnection(tracks[2], switches[0], TSimInterface.SWITCH_RIGHT);
        tracks[2].addConnection(tracks[3], switches[1], TSimInterface.SWITCH_LEFT);
        tracks[2].addConnection(tracks[4], switches[1], TSimInterface.SWITCH_RIGHT);
        tracks[3].addConnection(tracks[5], switches[2], TSimInterface.SWITCH_RIGHT);
        tracks[4].addConnection(tracks[5], switches[2], TSimInterface.SWITCH_LEFT);
        tracks[5].addConnection(tracks[6], switches[3], TSimInterface.SWITCH_RIGHT);
        tracks[5].addConnection(tracks[7], switches[3], TSimInterface.SWITCH_LEFT);

        tracks[10].addConnection(tracks[8], switches[0], TSimInterface.SWITCH_LEFT);
        tracks[10].addConnection(tracks[9], switches[0], TSimInterface.SWITCH_RIGHT);
        tracks[11].addConnection(tracks[10], switches[1], TSimInterface.SWITCH_LEFT);
        tracks[12].addConnection(tracks[10], switches[1], TSimInterface.SWITCH_RIGHT);
        tracks[13].addConnection(tracks[11], switches[2], TSimInterface.SWITCH_RIGHT);
        tracks[13].addConnection(tracks[12], switches[2], TSimInterface.SWITCH_LEFT);
        tracks[14].addConnection(tracks[13], switches[3], TSimInterface.SWITCH_RIGHT);
        tracks[15].addConnection(tracks[13], switches[3], TSimInterface.SWITCH_LEFT);
  }

  public static Section getSection(Position sensorPos){
    return sensorToSectionMapping.get(sensorPos);
  }

  public static void addMapping(Position sensorPos, Section section){
    sensorToSectionMapping.put(sensorPos, section);
  }

  public static void clearMapping(){
    sensorToSectionMapping.clear();
  }

  public static boolean containsSensor(Position sensorPos){
    return sensorToSectionMapping.containsKey(sensorPos);
  }

  public static enum Direction{
    TowardsA,
    TowardsB
  }

  public static class Position{
    private int x;
    private int y;

    public Position(int x, int y){
      this.x = x;
      this.y = y;
    }

    public int getX(){
      return x;
    }

    public int getY(){
      return y;
    }

    @Override
    public boolean equals(Object o){
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;

      Position position = (Position) o;

      if(x != position.x) return false;
      return y == position.y;
    }

    @Override
    public int hashCode(){
      int result = x;
      result = 31 * result + y;
      return result;
    }
  }

  public static class Section{
    protected Direction direction;
    protected Semaphore semaphores;
    protected Position sensorPos;
    protected ArrayList<Track> tracks;

    public Section(Direction direction, Semaphore semaphores, Position sensorPos){
      this.direction = direction;
      this.semaphores = semaphores;
      this.sensorPos = sensorPos;
      this.tracks = new ArrayList<>();
      Map.addMapping(sensorPos, this);
    }

    public Direction getDirection(){
      return direction;
    }

    public Semaphore getSemaphore(){
      return semaphores;
    }

    public Position getSensorPos(){
      return sensorPos;
    }

    public void addTrack(Track track){
      tracks.add(track);
    }

    public ArrayList<Track> getTracks(){
      return tracks;
    }
  }

  public static class Track extends Section{
    private ArrayList<Connection> connections;

    public Track(Direction direction, Semaphore semaphores, Position sensorPos){
      super(direction, semaphores, sensorPos);
      this.connections = new ArrayList<>();
    }

    public Direction getDirection(){
      return direction;
    }

    public Semaphore getSemaphore(){
      return semaphores;
    }

    public Position getSensorPos(){
      return sensorPos;
    }

    public void addConnection(Track track, Position switchPos, int switchDir){
      connections.add(new Connection(track, switchPos, switchDir));
    }

    public ArrayList<Connection> getConnections(){
      return connections;
    }
  }

  public static class Connection{
    private Track track;
    private Position switchPos;
    private int switchDir;

    public Connection(Track track, Position switchPos, int switchDir){
      this.track = track;
      this.switchPos = switchPos;
      this.switchDir = switchDir;
    }

    public Track getTrack(){
      return track;
    }

    public Position getSwitchPos(){
      return switchPos;
    }

    public int getSwitchDir(){
      return switchDir;
    }
  }
}
