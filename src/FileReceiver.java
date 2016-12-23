import java.io.OutputStream;
import java.net.DatagramSocket;

/**
 * Finate State Machine (FSM) Java Example: Woman
 * (lecture Slides for first lecture, p. 19)
 */


/**
 * Class which models the state machine itself.
 *
 */
public class FileReceiver {
	private boolean onceThru = false;
	private OutputStream fileStream;
	private DatagramSocket socket;
	
	// all states for this FSM
	enum State {
	WAIT0, WAIT1, WAIT_FOR_CONNECTION
	};
	// all messages/conditions which can occur
	enum Msg {
	SEQ0,SEQ1, INCOMMING_CONNECTION
	}
	// current state of the FSM	
	private State currentState;
	// 2D array defining all transitions that can occur
	private Transition[][] transition;
	
	/**
	 * constructor
	 */
	public FileReceiver(){
		currentState = State.WAIT0;
		// define all valid state transitions for our state machine
		// (undefined transitions will be ignored)
		transition = new Transition[State.values().length] [Msg.values().length];
		transition[State.WAIT_FOR_CONNECTION.ordinal()] [Msg.INCOMMING_CONNECTION.ordinal()] = new EstablishConnection();
		transition[State.WAIT0.ordinal()] [Msg.SEQ0.ordinal()] = new ProcessPacket();
		transition[State.WAIT0.ordinal()] [Msg.SEQ1.ordinal()] = new ResendAck();
		transition[State.WAIT1.ordinal()] [Msg.SEQ0.ordinal()] = new ProcessPacket();
		transition[State.WAIT1.ordinal()] [Msg.SEQ1.ordinal()] = new ResendAck();
		System.out.println("INFO FSM constructed, current state: "+currentState);
	}
	
	/**
	 * Process a message (a condition has occurred).
	 * @param input Message or condition that has occurred.
	 */
	public void processMsg(Msg input) {
		System.out.println("INFO Received "+input+" in state "+currentState);
		Transition trans = transition[currentState.ordinal()][input.ordinal()];
		if(trans != null){
			currentState = trans.execute(input);
		}
		System.out.println("INFO State: "+currentState);
	}
	
	/**
	 * Abstract base class for all transitions.
	 * Derived classes need to override execute thereby defining the action
	 * to be performed whenever this transition occurs.
	 */
	abstract class Transition {
		abstract public State execute(Msg input);
	}
	
	class ProcessPacket extends Transition {		
		@Override
		public State execute(Msg input) {
			//process packet
			//sendAck(input.ordinal())
			onceThru = true;
			return State.values()[Math.abs(input.ordinal() - 1)];
		}
	}
	
	class ResendAck extends Transition {
		@Override
		public State execute(Msg input) {
			//send Ack(input.ordinal())
			return currentState;
		}
	}
	
	class EstablishConnection extends Transition {
		@Override
		public State execute(Msg input) {
			//
			onceThru = false;
			return State.WAIT0;
		}
	}
}
