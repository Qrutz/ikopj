#!/bin/bash

# Run Lab 1 with proper environment setup
# Usage: ./run_lab.sh [train1_speed] [train2_speed] [simulator_speed]

# Set default values
TRAIN1_SPEED=${1:-5}
TRAIN2_SPEED=${2:-10}
SIMULATOR_SPEED=${3:-20}

# Setup tsim environment
source ./setup_tsim.sh

# Compile if needed
make

# Run the lab
echo "Starting Lab 1 simulation..."
echo "Train 1 speed: $TRAIN1_SPEED"
echo "Train 2 speed: $TRAIN2_SPEED"
echo "Simulator speed: $SIMULATOR_SPEED"
echo ""

java -cp bin Main Lab1.map $TRAIN1_SPEED $TRAIN2_SPEED $SIMULATOR_SPEED
