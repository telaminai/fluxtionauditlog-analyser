import json

with open("evidence/EXPECTED.json") as f:
    data = json.load(f)

data["scenarios"]["F"]["rows"][0]["price"] = 999.99

with open("evidence/EXPECTED-tampered-F-price.json", "w") as f:
    json.dump(data, f, indent=2)

print("wrote evidence/EXPECTED-tampered-F-price.json with scenario F price deliberately wrong (999.99 instead of 7.75)")
