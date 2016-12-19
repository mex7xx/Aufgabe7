/**
 * Created by mx on 19.12.16.
 */
public class FileSenderFSM {
    /**
     * Finate State Machine (FSM) Java Example: Woman
     * (lecture Slides for first lecture, p. 19)
     */

    /**
     * Class which models the state machine itself.
     *
     */

    // all states for this FSM
    enum State {
        WAIT0, WAIT0ACK, WAIT1, WAIT1ACK
    };
    // all messages/conditions which can occur
    enum Msg {
        RDT_SEND, TIMEOUT, RECEIVE_NOTCORRUPT_ISACK
        //MEET_MAN, HI, TIME
    }
    // current state of the FSM
    private State currentState;
    // 2D array defining all transitions that can occur
    private Transition[][] transition;

    /**
     * Main
     * @param args
     */
    public static void main(String[] args) {
        FileSenderFSM FSM = new FileSenderFSM();

        /**
         if(notCorrupt(pkt)) {
         FSM.processMsg(Msg.RDT_SEND, 10, -1);
         }
         **/

        /**woman.processMsg(Msg.MEET_MAN);
         woman.processMsg(Msg.HI);
         woman.processMsg(Msg.TIME);
         **/
    }

    /**
     * constructor
     */
    public FileSenderFSM(){
        currentState = State.WAIT0;
        // define all valid state transitions for our state machine
        // (undefined transitions will be ignored)
        transition = new Transition[State.values().length] [Msg.values().length];

        transition[State.WAIT0.ordinal()] [Msg.RDT_SEND.ordinal()]  = new RDT_send();                       // Wait0 -> Wait0ACK
        transition[State.WAIT0ACK.ordinal()] [Msg.TIMEOUT.ordinal()] = new Timeout();                       // Wait0ACK -> Wait0ACK
        transition[State.WAIT0ACK.ordinal()] [Msg.RECEIVE_NOTCORRUPT_ISACK.ordinal()]  = new Receive();     // Wait0ACK -> Wait1
        transition[State.WAIT1.ordinal()] [Msg.RDT_SEND.ordinal()] = new RDT_send();                        // Wait1 -> Wait1ACK
        transition[State.WAIT1ACK.ordinal()] [Msg.TIMEOUT.ordinal()] = new Timeout();                       // Wait1ACK -> Wait1ACK
        transition[State.WAIT1ACK.ordinal()] [Msg.RECEIVE_NOTCORRUPT_ISACK.ordinal()] = new Receive();      // Wait1ACK -> Wait0

        /**
         transition[State.IDLE.ordinal()] [Msg.MEET_MAN.ordinal()] = new SayHi();
         transition[State.HI_WAIT.ordinal()] [Msg.HI.ordinal()] = new AskForTime();
         transition[State.TIME_WAIT.ordinal()] [Msg.TIME.ordinal()] = new Finish();
         **/
        System.out.println("INFO FSM constructed, current state: "+currentState);
    }
    /**
     * Process a message (a condition has occurred).
     * @param input Message or condition that has occurred.
     */
    public void processMsg(Msg input, int data, int rcvpkt){
        System.out.println("INFO Received "+input+" in state "+currentState);
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        if(trans != null) {
            currentState = trans.execute(input, data, rcvpkt);
        }
        System.out.println("INFO State: "+ currentState);
    }
    /**
     * Abstract base class for all transitions.
     * Derived classes need to override execute thereby defining the action
     * to be performed whenever this transition occurs.
     */
    abstract class Transition {
        abstract public State execute(Msg input, int data, int rcvpkt);
    }

    class RDT_send extends Transition {
        @Override
        public State execute(Msg input, int data, int rcvpkt) {
            // currentState -> if
            // hier Code von Übergang:
            // sndpkt = make_pkt(0,data,checksum)
            // udt_send(sndpkt)
            // start_timer()
            return State.WAIT0ACK;
        }
    }

    class Timeout extends Transition {
        @Override
        public State execute(Msg input, int data, int rcvpkt) {
            //udt_send(sendpkt)
            // start timer
            System.out.println("");
            return State.WAIT1ACK;
        }
    }

    class Receive extends Transition {
        @Override
        public State execute(Msg input, int data, int rcvpkt) {
            if (currentState == State.WAIT0ACK) return State.WAIT1;
            else return State.WAIT0;
        }
    }

    /**
    class SayHi extends Transition {
        @Override
        public State execute(Msg input) {
            System.out.println("Hi!");
            return State.HI_WAIT;
        }
    }

    class AskForTime extends Transition {
        @Override
        public State execute(Msg input) {
            System.out.println("Time?");
            return State.TIME_WAIT;
        }
    }

    class Finish extends Transition {
        @Override
        public State execute(Msg input) {
            System.out.println("Thank you.");
            return State.IDLE;
        }

    }
     **/
}



