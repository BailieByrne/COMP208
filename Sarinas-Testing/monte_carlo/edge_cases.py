import subprocess
from pathlib import Path
import time

exe = Path("/Users/sarinasaiyed/COMP208/Backend/program")
test_folder = Path("/Users/sarinasaiyed/COMP208/Sarinas-Testing/edge_case_tests")

def test_cases():
    test_folder.mkdir(exist_ok=True)
    
