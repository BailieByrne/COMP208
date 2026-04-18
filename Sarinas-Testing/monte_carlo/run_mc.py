#I analysed internal performance breakdown (simulation vs AI)
import subprocess
from pathlib import Path
import time
from datetime import datetime


exe = Path("/Users/sarinasaiyed/COMP208/Backend/program")
outer_folder = Path("/Users/sarinasaiyed/COMP208/Sarinas-Testing/outputs")
runs = 75

def run_simulation():  
    if not outer_folder.exists():
        outer_folder.mkdir()

    log_file = outer_folder / "timing_log.txt"

    start_time_total = time.time()

    with open(log_file, 'w') as log:
        log.write(f"MC test run: {datetime.now()} \n") 
        log.write("=" * 60 + "\n\n")

        for i in range(1, runs + 1):
            start_time = time.time()

            run_directory = outer_folder / f"run_{i}"        
            run_directory.mkdir(exist_ok = True)

            try:
                ticker = "AAPL"
                difficulty = "2"
                S0 = "500.0"
                seed = f"run_{i}"
                subprocess.run(
                    [str(exe), ticker,str(difficulty), str(S0), seed],
                    cwd=run_directory, 
                    check=True)
                ###output_file = run_directory / "predicted_prices.csv"
               # if (output_file).exists():
              #      status = "Completed"
              #      print(f" \n Run #{i} completed successfully")
              #  else:
              #      status = "No_CSV"
              #     print(f" \n Run #{i} finished with no CSV file found")
              # ai code start
                csv1 = run_directory / f"{ticker}_stock_prices.csv"
                csv2 = run_directory / f"{ticker}_predicted_prices.csv"

                if csv1.exists() and csv2.exists():
                    status = "Completed"
                else:
                    status = "No_CSV"
# ai code end  

            except subprocess.CalledProcessError as e:
                status = f"failed: {e}"
                print(f" \n Run #{i} failed with error: {e}")

            
            # calculating how long the run time takes
            end_time = time.time()
            run_length_time = end_time - start_time

            #linking c++ timing.txt to pythong analysis
            timing_file = run_directory / "timing.txt"

            sim_time = None
            ai_time = None
            ccp_total = None

            if timing_file.exists():
                with open(timing_file, 'r') as file:   
                    for line in file:
                        line = line.strip()
                        
                        #skipping to next line if empty or doesnt have :
                        if not line or ":" not in line:
                            continue
                        
                        header, data = line.split(":", 1)
                        header = header.strip()
                        data = data.replace("s", "").strip()

                        try:
                            data = float(data)
                        except:
                            continue
                        
                        if header == "Simulation":
                            sim_time = data
                        elif header == "AI":
                            ai_time = data
                        elif header == "Total":
                            ccp_total = data

#sanity check for the c++ data
            if sim_time is None or ai_time is None or ccp_total is None:
                print(f"Missing timing data in run{i}")
#
            print(f" Time taken: {run_length_time:.2f} seconds")
            log.write(
                f"Run {i}: Python={run_length_time:.4f}s | "
                f"C++ Total={ccp_total:.4f}s | "
                f"Sim={safe(sim_time):.4f}s | AI={safe(ai_time):.4f}s\n"
            )
    
        #calculating total time
        end_time_total = time.time()
        total_time = end_time_total - start_time_total

        log.write("\n" + "=" * 60 + "\n")
        log.write(f"Total runs: {runs}\n")
        log.write(f"Total time: {total_time:.2f} seconds\n")
        log.write(f"Average time per run: {total_time/runs:.2f} seconds\n")

    print("\n" + "=" * 50)
    print("All simulations complete")
    print(f"Total time: {total_time:.2f}")
    print(f"Log saved to: {log_file}")


# Ai code remove later
def safe(v):
    return 0.0 if v is None else v
# end of ai code

    

if __name__ == "__main__":
    run_simulation()
        
