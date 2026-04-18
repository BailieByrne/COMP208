from pathlib import Path
import statistics
from datetime import datetime

outer_folder = Path("/Users/sarinasaiyed/COMP208/Sarinas-Testing/outputs")

def analysis():
    log_file = outer_folder / "timing_log.txt"
    
    if not log_file.exists():
        print("No logs found")
        return
    
    #reading time log file
    times = []
    with open(log_file, 'r') as file:   
        for line in file:
           if line.startswith("Run "): #reading the file and skipping everything that doesnt start w that
               time_value = line.split("Python=")[1].split("s")[0] #taking out just the time value
               times.append(float(time_value))

    if not times:
        print("No timing data found")
        return
    

    #calculating the anyasis stats
    avg_time = statistics.mean(times)
    min_time = min(times)
    max_time = max(times)

    #working out how consistent each times are with one another (sd + v)
    if len(times) > 1:
        variance = statistics.variance(times)
        std_dev = statistics.stdev(times)
    else:
        variance = 0
        std_dev = 0

    print("=" * 50)
    print(f"Total runs analysed: {len(times)}")
    print(f"Average time: {avg_time:.2f} seconds")
    print(f"Fastest run: {min_time:.2f} seconds")
    print(f"Slowest run: {max_time:.2f} seconds")
    print(f"Standard deviation: {std_dev:.2f} seconds")
    print(f"Variance: {variance:.2f}")

    summary_file = Path("/Users/sarinasaiyed/COMP208/Sarinas-Testing/outputs/summary.txt")
    with open(summary_file, 'w') as sumry:
        sumry.write(f"MC test run: {datetime.now()} \n") 
        sumry.write("=" * 60 + "\n\n")
        sumry.write(f"Total runs: {len(times)}\n")
        sumry.write(f"Average runtime: {avg_time:.2f}s\n")
        sumry.write(f"Min runtime: {min_time:.2f}s\n")
        sumry.write(f"Max runtime: {max_time:.2f}s\n")
        sumry.write(f"Standard deviation: {std_dev:.2f}s\n")
        sumry.write(f"Variance: {variance:.2f}\n")

if __name__ == "__main__":
    analysis()
               
            
