import pandas as pd
import numpy as np
from datetime import datetime
import os
import subprocess

# Create Logs folder if it doesn't exist
log_folder = "Logs"
if not os.path.exists(log_folder):
    os.makedirs(log_folder)

# Number of trials
num_trials = 500
exe_path = r".\build\bin\Release\stock_sim.exe"

errors = []

print(f"Starting {num_trials} trials...")
print()

for trial in range(1, num_trials + 1):
    # Run the executable
    try:
        subprocess.run(exe_path, check=True, capture_output=True)
    except subprocess.CalledProcessError as e:
        print(f"Trial {trial}: Error running executable")
        continue
    
    # Read the generated CSV files
    try:
        predicted_prices = pd.read_csv('predicted_prices.csv')
        stock_prices = pd.read_csv('stock_prices.csv')
        
        # Extract the price columns
        predicted = predicted_prices['Price'].values
        actual = stock_prices['Price'].values
        
        # Calculate L2 error
        l2_error = np.sqrt(np.sum((predicted - actual) ** 2))
        errors.append(l2_error)
        
        print(f"Trial {trial}/{num_trials}: L2 Error = {l2_error:.4f}")
    except Exception as e:
        print(f"Trial {trial}: Error reading/processing CSV - {e}")
        continue

# Calculate statistics
if errors:
    avg_error = np.mean(errors)
    min_error = np.min(errors)
    max_error = np.max(errors)
    std_error = np.std(errors)
    
    print()
    print("=" * 50)
    print("BATCH RESULTS")
    print("=" * 50)
    print(f"Total Trials: {len(errors)}")
    print(f"Average L2 Error: {avg_error:.4f}")
    print(f"Min L2 Error: {min_error:.4f}")
    print(f"Max L2 Error: {max_error:.4f}")
    print(f"Std Dev: {std_error:.4f}")
    print("=" * 50)
    
    # Log to file
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    log_file = os.path.join(log_folder, f"batch_error_{timestamp}.txt")
    
    with open(log_file, 'w') as f:
        f.write(f"Batch Error Detection Results\n")
        f.write(f"Timestamp: {datetime.now()}\n")
        f.write(f"Number of Trials: {len(errors)}\n")
        f.write(f"Average L2 Error: {avg_error:.4f}\n")
        f.write(f"Min L2 Error: {min_error:.4f}\n")
        f.write(f"Max L2 Error: {max_error:.4f}\n")
        f.write(f"Std Dev: {std_error:.4f}\n")
    
    print(f"Logged to: {log_file}")
else:
    print("No valid trials completed")
