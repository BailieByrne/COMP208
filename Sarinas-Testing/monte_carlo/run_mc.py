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
                subprocess.run([str(exe)],
                              cwd=run_directory,
                              check = True)
        
                output_file = run_directory / "predicted_prices.csv"
                if (output_file).exists():
                    status = "Completed"
                    print(f" \n Run #{i} completed successfully")
                else:
                    status = "No_CSV"
                    print(f" \n Run #{i} finished with no CSV file found")

            except subprocess.CalledProcessError as e:
                status = f"failed: {e}"
                print(f" \n Run #{i} failed with error: {e}")

            # calculating how long the run time takes
            end_time = time.time()
            run_length_time = end_time - start_time

            print(f" Time taken: {run_length_time:.2f} seconds")
            log.write(f"Run {i}: {run_length_time:.2f}s - {status}\n")
    
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

    

if __name__ == "__main__":
    run_simulation()
        
