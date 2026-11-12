"""Estimate fully upgraded (level 3) car stats from NFSU2 GLOBAL/GLOBALB.BUN.

Usage: python -I scripts/car_stats.py <game>/GLOBAL/GLOBALB.BUN [--md]

Reads the CarTypeInfo chunk (0x00034600, 0x890 bytes per car) and prints a
ranking by power-to-weight. Field meanings are reverse-engineered; see
docs/CAR_PHYSICS_FULL_UPGRADE.md for the layout and the model's caveats.
"""
import math
import struct
import sys

CAR_TYPE_INFO = 0x00034600
RECORD = 0x890
PLAYER_CARS = 31  # records 31..45 are traffic


def find_chunk(data, wanted):
    def walk(off, end):
        while off + 8 <= end:
            cid, size = struct.unpack_from('<II', data, off)
            if off + 8 + size > end:
                return None
            if cid == wanted:
                return off + 8, size
            if cid & 0x80000000:
                hit = walk(off + 8, off + 8 + size)
                if hit:
                    return hit
            off += 8 + size
        return None
    return walk(0, len(data))


def main():
    data = open(sys.argv[1], 'rb').read()
    as_md = '--md' in sys.argv
    start, size = find_chunk(data, CAR_TYPE_INFO)
    while data[start] == 0x11:  # alignment padding before the first record
        start += 1

    rows = []
    for i in range(PLAYER_CARS):
        r = start + i * RECORD
        f = lambda o: struct.unpack_from('<f', data, r + o)[0]
        fa = lambda o, n: [f(o + 4 * k) for k in range(n)]
        name = data[r:r + 32].split(b'\0')[0].decode()

        mass = f(0x220) * (1 - f(0x710))           # tonnes, after weight reduction
        idle, redline = f(0x300), f(0x304)
        torque = fa(0x310, 9)                      # stock curve, kN*m
        eng3 = fa(0x5f0, 9)                        # engine level 3 torque add
        redline3 = redline + f(0x5e4)
        turbo3 = fa(0x624, 8)                      # turbo level 3 boost curve

        best_kw, peak_t = 0.0, 0.0
        for k in range(9):
            x = k / 8
            rpm = idle + x * (redline3 - idle)
            tj = x * 7
            a = int(min(tj, 6))
            boost = turbo3[a] + (turbo3[a + 1] - turbo3[a]) * (tj - a)
            t = torque[k] + eng3[k] + boost
            peak_t = max(peak_t, t)
            best_kw = max(best_kw, t * rpm * 2 * math.pi / 60)
        hp = best_kw * 1.341

        split = f(0x4f0)                           # rear torque share, level 3 gearbox
        drive = 'FWD' if split < 0.1 else ('RWD' if split > 0.9 else 'AWD')
        grip = (f(0x65c) + f(0x67c)) / 2           # upgraded tyres, front/rear average
        rows.append((name, drive, mass, peak_t * 1000, hp, redline3, hp / mass, grip))

    rows.sort(key=lambda row: -row[6])
    if as_md:
        print('| # | Car | Drive | Mass, t | Torque, Nm | hp | Redline | hp/t | Grip |')
        print('|---|---|---|---|---|---|---|---|---|')
        for n, (name, drive, m, t, hp, rl, pw, g) in enumerate(rows, 1):
            print(f'| {n} | {name} | {drive} | {m:.3f} | {t:.0f} | {hp:.0f} | {rl:.0f} | {pw:.0f} | {g:.3f} |')
    else:
        print(' #  car          drive mass_t torque_Nm   hp redline hp/t  grip')
        for n, (name, drive, m, t, hp, rl, pw, g) in enumerate(rows, 1):
            print(f'{n:2}  {name:12} {drive}  {m:6.3f} {t:9.0f} {hp:4.0f} {rl:7.0f} {pw:4.0f} {g:5.3f}')


if __name__ == '__main__':
    main()
