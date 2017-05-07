import os
import json
from collections import defaultdict
import numpy as np
import sys

directory_path = '/Users/Achilles/ggp-saved-matches/'
player_stats = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
 
for fn in os.listdir(directory_path):
	with open(directory_path + fn, 'rb') as f:
		parsed_json = json.loads(f.read())
		fn = fn.split(".")[1]
		names = parsed_json["playerNamesFromHost"]
		goalValues = parsed_json["goalValues"]
		for i in range(len(names)):
			player = names[i]
			for j in (range(i) + range(i + 1, len(names))):
				opponent = names[j]
				player_stats[player][opponent][fn].append(goalValues[i])

for player in player_stats:
	for opponent in player_stats[player]:
		print opponent
		for game in player_stats[player][opponent]:
			print game
			stats = np.array(player_stats[player][opponent][game])
			summary = ""
			summary += str(np.mean(stats)) + " +/- "
			summary += str(2 * np.std(stats))
			print summary
		print ""

import pandas
print pandas.DataFrame(range(3), ["hi", "bye", "hid"], ["a"])

