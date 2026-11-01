"""Render a /biblioteca diagnostics export as a standalone, offline light-section viewer."""
import argparse
import json
from pathlib import Path


PAGE = r'''<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Biblioteca light section</title>
<style>
body{margin:0;background:#10151c;color:#e6edf3;font:15px system-ui,sans-serif}main{max-width:1000px;margin:32px auto;padding:0 24px}
h1{font-size:26px;margin-bottom:8px}p{color:#aab8c8;line-height:1.6}select{padding:8px;background:#202b38;color:inherit;border:1px solid #52677d;border-radius:6px}
.panel{display:flex;gap:24px;align-items:flex-start;margin-top:20px;flex-wrap:wrap}canvas{image-rendering:pixelated;max-width:100%;border:1px solid #52677d}
aside{max-width:400px;position:sticky;top:20px}pre{white-space:pre-wrap;background:#202b38;padding:16px;line-height:1.7;border-radius:8px}
</style><main><h1>Biblioteca light section</h1><p id="meta"></p>
<label>Layer <select id="mode"><option value="serverSky">Server sky</option><option value="clientSky">Client sky</option>
<option value="deltaSky">Client − server sky</option><option value="serverBlock">Server block light</option>
<option value="clientBlock">Client block light</option><option value="deltaBlock">Client − server block light</option></select></label>
<div class="panel"><canvas id="section"></canvas><aside><p>Top of the image is the dimension's top row. Each column is one X coordinate; Z is fixed.</p>
<p>Light: dark to bright = 0 to 15.<br>Difference: orange = client brighter, blue = client darker, pale = equal.<br>Gray = unavailable or unloaded.</p>
<p>Hover a cell to inspect it. Server and client samples are taken on their own threads at the recorded times; a difference during updates is not proof of a bug.</p>
<pre id="cell">Move the pointer over the section.</pre></aside></div></main>
<script>
const data=__DATA__;
const canvas=document.getElementById('section'),ctx=canvas.getContext('2d'),mode=document.getElementById('mode');
const xs=[...new Set(data.samples.map(p=>p.x))].sort((a,b)=>a-b),columns=new Map(xs.map((x,i)=>[x,i]));
const lookup=new Map(data.samples.map(p=>[p.x+':'+p.y,p]));
const cellWidth=22,cellHeight=2;
canvas.width=xs.length*cellWidth;canvas.height=data.height*cellHeight;
document.getElementById('meta').textContent=`${data.dimension} · Y ${data.minimumY}…${data.minimumY+data.height-1} · looping ${data.looping} · sky ${data.skyMode} · server tick ${data.serverGameTime} · client tick ${data.clientGameTime??'not sampled'}`;
function value(p){
 if(mode.value==='deltaSky')return typeof p.clientSky==='number'&&typeof p.serverSky==='number'?p.clientSky-p.serverSky:null;
 if(mode.value==='deltaBlock')return typeof p.clientBlock==='number'&&typeof p.serverBlock==='number'?p.clientBlock-p.serverBlock:null;
 return typeof p[mode.value]==='number'?p[mode.value]:null;
}
function color(v){
 if(v===null)return '#687080';
 if(mode.value.startsWith('delta')){const t=Math.min(15,Math.abs(v))/15;return v===0?'#e8edf1':v>0?`rgb(255,${Math.round(220-150*t)},${Math.round(200-180*t)})`:`rgb(${Math.round(210-180*t)},${Math.round(225-110*t)},255)`;}
 const t=Math.max(0,Math.min(15,v))/15;return `rgb(${Math.round(12+210*t)},${Math.round(25+220*t)},${Math.round(45+210*t)})`;
}
function draw(){ctx.fillStyle='#687080';ctx.fillRect(0,0,canvas.width,canvas.height);for(const p of data.samples){ctx.fillStyle=color(value(p));ctx.fillRect(columns.get(p.x)*cellWidth,(data.minimumY+data.height-1-p.y)*cellHeight,cellWidth,cellHeight);}}
canvas.addEventListener('mousemove',e=>{const r=canvas.getBoundingClientRect(),col=Math.floor((e.clientX-r.left)/r.width*xs.length),y=data.minimumY+data.height-1-Math.floor((e.clientY-r.top)/r.height*data.height);const p=lookup.get(xs[col]+':'+y);if(p)document.getElementById('cell').textContent=`Block: ${p.x}, ${p.y}, ${p.z}\n${p.block??'unloaded'}\nServer sky / block: ${p.serverSky??'?'} / ${p.serverBlock??'?'}\nClient sky / block: ${p.clientSky??'?'} / ${p.clientBlock??'?'}\nSelected value: ${value(p)??'unavailable'}`;});
mode.addEventListener('change',draw);draw();
</script></html>
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('snapshot', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    data = json.loads(args.snapshot.read_text(encoding='utf-8'))
    if data.get('schemaVersion') != 1 or not isinstance(data.get('samples'), list) or not data['samples']:
        raise SystemExit('Expected a non-empty Biblioteca diagnostic snapshot (schemaVersion 1).')
    if not isinstance(data.get('height'), int) or not 0 < data['height'] <= 4096:
        raise SystemExit('Snapshot has an invalid dimension height.')
    encoded = json.dumps(data, ensure_ascii=True).replace('<', '\\u003c').replace('>', '\\u003e').replace('&', '\\u0026')
    output = args.output or args.snapshot.with_suffix('.html')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(PAGE.replace('__DATA__', encoded), encoding='utf-8')
    print('DIAGNOSTIC_VIEWER ' + str(output.resolve()))


if __name__ == '__main__':
    main()
