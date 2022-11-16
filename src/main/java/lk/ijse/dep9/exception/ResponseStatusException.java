package lk.ijse.dep9.exception;

public class ResponseStatusException extends RuntimeException{

    private int status;

        public ResponseStatusException(int statusCode, String message, Throwable t){
            super(message, t);
            this.status = status;
        }

        public ResponseStatusException(int statusCode, Throwable t){
            super(t);
            this.status = status;
        }

        public int getString(){
            return status;
        }

}
