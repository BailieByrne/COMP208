import socket
import time
import random
from datetime import datetime

delay = 1

HOST = "127.0.0.1"   # server machine IP (localhost for same PC)
PORT = 3000         # must match Java PORT

def main():
    timeElapsed = 0
    while True:
        try:
            print(f"Connecting to {HOST}:{PORT} ...")
            with socket.create_connection((HOST, PORT), timeout=10) as sock:
                # Use a file-like wrapper for easy line sending
                f = sock.makefile("w", encoding="utf-8", newline="\n")

                print(f"Connected. Sending CSV every {delay} seconds. Ctrl+C to stop.")
                while True:
                    ts = datetime.utcnow().isoformat(timespec="seconds")
                    value = random.randint(0, 1000)

                    csv_line = f"{timeElapsed} {value}\n"


                    f.write(csv_line)
                    f.flush()

                    print("Sent:", csv_line.strip())
                    timeElapsed += 1
                    time.sleep(delay)

        except (ConnectionRefusedError, socket.timeout, OSError) as e:
            print("Connection error:", e)
            print("Retrying in 2 seconds...")
            time.sleep(2)

if __name__ == "__main__":
    main()
