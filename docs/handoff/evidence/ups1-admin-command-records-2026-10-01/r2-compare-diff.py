import sys,collections,os
def load(p):
    d={}
    for l in open(p,encoding='utf-8'):
        f=l.rstrip('\n').split('\t',4)
        if len(f)<5: continue
        d[(f[0],f[1],f[2],f[3])]=f[4]
    return d
a,b=load(sys.argv[1]),load(sys.argv[2]); verbose=len(sys.argv)>3
keys=sorted(set(a)|set(b))
ch=[k for k in keys if a.get(k)!=b.get(k)]
by=collections.Counter((os.path.basename(k[0]),k[1]) for k in ch)
paths=collections.Counter(k[1] for k in ch)
recs=len({(k[0],k[1],k[2]) for k in ch})
print('changed fields:',len(ch),'records:',recs,'by path:',dict(paths))
for (f,p),n in sorted(by.items()): print(f'  {n:4} {p:20} {f}')
if verbose:
    for k in ch: print(' ',os.path.basename(k[0]),k[1],'rec',k[2],k[3],':',a.get(k),'->',b.get(k))
