import pandas as pd
import numpy as np
from datetime import datetime
import os

# Create Logs folder if it doesn't exist
log_folder = "Logs"
if not os.path.exists(log_folder):
    os.makedirs(log_folder)

# Read the CSV files
predicted_prices = pd.read_csv('predicted_prices.csv')
stock_prices = pd.read_csv('stock_prices.csv')

# Extract the price columns
predicted = predicted_prices['Price'].values
actual = stock_prices['Price'].values

# Calculate L2 error (Euclidean distance)
l2_error = np.sqrt(np.sum((predicted - actual) ** 2))

# Log to file with timestamp
timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
log_file = os.path.join(log_folder, f"l2_error_{timestamp}.txt")

with open(log_file, 'w') as f:
    f.write(f"L2 Error: {l2_error}\n")
    f.write(f"Timestamp: {datetime.now()}\n")

print(f"L2 Error: {l2_error}")
print(f"Logged to: {log_file}")
