import socket
import time
import random
import csv
from io import StringIO

HOST = "127.0.0.1"
PORT = 5000


def make_csv_line(*fields):
    buf = StringIO()
    writer = csv.writer(buf, lineterminator="\n")
    writer.writerow(fields)
    return buf.getvalue().encode("utf-8")


def start_server():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((HOST, PORT))
        server.listen(1)

        print("[PY] Waiting for Java client...")

        conn, addr = server.accept()
        print(f"[PY] Connected from {addr}")

        with conn:
            file = conn.makefile("rwb")

            # ---- WAIT FOR START SIGNAL ----
            line = file.readline().decode().strip()
            print("[PY] Received:", line)

            if line != "START":
                print("[PY] Invalid start signal.")
                return

            # acknowledge readiness
            file.write(b"ACK\n")
            file.flush()

            print("[PY] Streaming data...")

            # optional header
            file.write(make_csv_line("ts_ms", "symbol", "price"))
            file.flush()

            while True:
                ts = int(time.time() * 1000)
                price = round(100 + random.random() * 10, 3)

                file.write(make_csv_line(ts, "AAPL", price))
                file.flush()

                time.sleep(1)


if __name__ == "__main__":
    start_server()