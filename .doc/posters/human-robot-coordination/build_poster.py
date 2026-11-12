"""Build the A0 poster with embedded fonts, vector figures and the full dialogue.

Run from the repository root: python .doc/posters/human-robot-coordination/build_poster.py
Dependencies: reportlab, svglib, pymupdf. Local optional install: target/poster-python.
"""
from pathlib import Path
import json
import re
import sys
from html import escape

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[2]
if (REPO / 'target/poster-python').exists():
    sys.path.insert(0, str(REPO / 'target/poster-python'))

from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph
from reportlab.lib.units import mm
from reportlab.graphics import renderPDF
from reportlab.graphics.renderbase import STATE_DEFAULTS
from svglib.svglib import svg2rlg
from svglib.fonts import register_font
import pymupdf as fitz

ASSETS = ROOT / 'assets'
OUT = REPO / 'output/pdf'
TMP = REPO / 'target/poster-print'
OUT.mkdir(parents=True, exist_ok=True)
TMP.mkdir(parents=True, exist_ok=True)
INK = '#0c1618'
MUTED = '#536365'
PAPER = '#f6f7f4'
TEAL = '#00b0a2'
DEEP_TEAL = '#006e67'
ORANGE = '#ff7a00'
LINE = '#d8dfdc'
WIDTH, HEIGHT = 1189*mm, 841*mm

for name in ['Regular', 'Medium', 'Bold']:
    pdfmetrics.registerFont(TTFont('SG-'+name, str(ASSETS / 'fonts' / f'SpaceGrotesk-{name}.ttf')))
    register_font('SG-'+name, font_path=str(ASSETS/'fonts'/f'SpaceGrotesk-{name}.ttf'), rlgFontName='SG-'+name)
pdfmetrics.registerFontFamily('SG',normal='SG-Regular',bold='SG-Bold',italic='SG-Regular',boldItalic='SG-Bold')
pdfmetrics.registerFontFamily('SG-Regular',normal='SG-Regular',bold='SG-Bold',italic='SG-Regular',boldItalic='SG-Bold')
handwriting = Path('C:/Windows/Fonts/segoeprb.ttf')
pdfmetrics.registerFont(TTFont('Hand', str(handwriting)))
STATE_DEFAULTS['fontName']='SG-Regular'


def svg_text(x, y, text, size=23, bold=False, colour=INK, anchor='start'):
    return f'<text x="{x}" y="{y}" font-family="SG-{("Bold" if bold else "Regular")}" font-size="{size}" fill="{colour}" text-anchor="{anchor}">{escape(text)}</text>'


def make_workflow():
    """Compact vector counterpart to the original five-stage figure."""
    a=['<svg xmlns="http://www.w3.org/2000/svg" width="2000" height="350" viewBox="0 0 2000 350">',
       '<style>@font-face{font-family:SG-Regular;src:url(fonts/SpaceGrotesk-Regular.ttf)}@font-face{font-family:SG-Bold;src:url(fonts/SpaceGrotesk-Bold.ttf)}</style>',
       '<title>A flexible workflow for human-robot coordination</title>',
       '<desc>Opening, Alignment, Planning, Execution and Reflection. Changed circumstances return to Alignment; reflection returns to Planning for a revised agreement.</desc>']
    xs=[0,404,808,1212,1616]
    names=['Opening','Alignment','Planning','Execution','Reflection']
    subtitles=['Discover possibilities','Clarify the task','Agree a plan','Act and coordinate','Improve the next run']
    rows=[
        [('HUMAN', ['What are you?', 'What can you do?', 'How can you make', 'yourself useful?', 'How about you do ...?'])],
        [('HUMAN', ['Are there questions', 'left unanswered?', 'Are there decisions', 'I need to make?'])],
        [('ROBOT', ['Here is what I can do.', 'Is that what you want?']), ('HUMAN', ['Yes, start now.'])],
        [('ACTION', ['Carry out the agreed task.']), ('HUMAN', ['Wait - something changed.']), ('ROBOT', ['I need your help.'])],
        [('ROBOT', ['Was that OK?', 'What should change', 'next time?'])]
    ]
    a.append('<path d="M1400 35V15H592V35" fill="none" stroke="#006e67" stroke-width="2.2"/>')
    a.append('<path d="M587 29L592 37L597 29" fill="none" stroke="#006e67" stroke-width="2.2"/>')
    a.append(svg_text(996,10,'Changed circumstances? Clarify and agree again.',19,True,DEEP_TEAL,'middle'))
    for i,x in enumerate(xs):
        a.append(f'<rect x="{x+1}" y="40" width="382" height="260" rx="10" fill="#ffffff" stroke="{LINE}"/>')
        a.append(f'<rect x="{x+16}" y="57" width="35" height="35" rx="8" fill="{ORANGE if i==2 else "#d9efeb"}"/>')
        a.append(svg_text(x+33.5,82,str(i+1),25,True,INK,'middle'))
        a.append(svg_text(x+63,83,names[i],30,True))
        a.append(svg_text(x+17,112,subtitles[i],21,False,MUTED))
        a.append(f'<line x1="{x+17}" y1="125" x2="{x+365}" y2="125" stroke="{LINE}"/>')
        yy=147
        for role,lines in rows[i]:
            a.append(svg_text(x+18,yy,role,15,True,DEEP_TEAL if role=='ROBOT' else MUTED))
            yy+=26
            for line in lines:
                a.append(svg_text(x+18,yy,line,23))
                yy+=25
            yy+=7
        if i<4:
            a.append(f'<path d="M{x+387} 88H{x+400}M{x+395} 83L{x+400} 88L{x+395} 93" fill="none" stroke="{DEEP_TEAL}" stroke-width="2"/>')
    # The execution card needs tighter spacing for three distinct roles.
    # Its last line remains within the 300-unit card boundary.
    a.append('<path d="M1807 306V324H999V306" fill="none" stroke="#006e67" stroke-width="2.2"/>')
    a.append('<path d="M994 312L999 304L1004 312" fill="none" stroke="#006e67" stroke-width="2.2"/>')
    a.append(svg_text(1403,348,'Agree changes before the next run.',19,True,DEEP_TEAL,'middle'))
    a.append(svg_text(1,340,'Speech and shared observations support every stage.',20,False,MUTED))
    a.append('</svg>')
    (ASSETS/'coordination-workflow-print.svg').write_text('\n'.join(a),encoding='utf-8')


make_workflow()
pdf = canvas.Canvas(str(TMP/'poster-base.pdf'), pagesize=(WIDTH,HEIGHT), pageCompression=1,
                    initialFontName='SG-Regular', initialFontSize=24)
pdf.setTitle('Human Robot Coordination')
pdf.setAuthor('ZHAW SIRA Lab | ZHAW Institut für Wirtschaftsinformatik')
pdf.setSubject('Coordination workflows, design guidelines, a full Valerian interaction and current challenges')
measurements=[]


def box(x,y,w,h,fill='white',stroke=None,r=4):
    pdf.setFillColor(HexColor(fill))
    pdf.setStrokeColor(HexColor(stroke or fill))
    pdf.setLineWidth(.7)
    pdf.roundRect(x*mm,HEIGHT-(y+h)*mm,w*mm,h*mm,r*mm,fill=1,stroke=bool(stroke))


def line(x1,y1,x2,y2,colour=LINE,width=.8):
    pdf.setStrokeColor(HexColor(colour));pdf.setLineWidth(width)
    pdf.line(x1*mm,HEIGHT-y1*mm,x2*mm,HEIGHT-y2*mm)


def label(text,x,y,size=28,bold=False,colour=INK):
    pdf.setFillColor(HexColor(colour))
    pdf.setFont('SG-Bold' if bold else 'SG-Regular',size)
    pdf.drawString(x*mm,HEIGHT-y*mm-size*.83,text)


def para(text,x,y,w,size=27,leading=None,bold=False,colour=INK,max_h=None):
    style=ParagraphStyle('p',fontName='SG-Bold' if bold else 'SG-Regular',fontSize=size,
                         leading=leading or size*1.24,textColor=HexColor(colour),spaceAfter=0)
    p=Paragraph(text,style)
    _,h=p.wrap(w*mm,10000)
    if max_h is not None and h>max_h*mm+.1:
        raise ValueError(f'Text overflow: {text[:60]} needs {h/mm:.1f} mm, available {max_h}')
    p.drawOn(pdf,x*mm,HEIGHT-y*mm-h)
    measurements.append({'text':text[:80],'x':x,'y':y,'width':w,'height':h/mm,'font_pt':size})
    return h/mm


def svg(path,x,y,w):
    d=svg2rlg(str(path));scale=w*mm/d.width
    d.scale(scale,scale)
    renderPDF.draw(d,pdf,x*mm,HEIGHT-y*mm-d.height*scale)


# Quiet paper and a geometric field borrowed from the site's visual vocabulary.
box(0,0,1189,841,PAPER,r=0)
box(0,61,1189,110,'#eef4f2',r=0)
pdf.saveState()
clip=pdf.beginPath();clip.rect(0,HEIGHT-171*mm,WIDTH,110*mm);pdf.clipPath(clip,stroke=0)
for xx in range(650,1350,23):line(xx,61,xx-30,171,'#e0e9e5',.55)
for yy in range(69,180,18):line(610,yy,1189,yy-26,'#e0e9e5',.55)
pdf.restoreState()

# Compact institutional header.
box(25,23,18,18,'#dcece4',LINE,r=3)
pdf.saveState();clip=pdf.beginPath();clip.roundRect(25*mm,HEIGHT-41*mm,18*mm,18*mm,3*mm)
pdf.clipPath(clip,stroke=0)
pdf.linearGradient(25*mm,HEIGHT-23*mm,43*mm,HEIGHT-41*mm,[HexColor('#ffe3c9'),HexColor('#d4eee8')])
pdf.restoreState()
label('S',30.9,26.7,32,True)
label('ZHAW SIRA Lab',50,22.7,33,True)
label('Socially Intelligent and Responsible Agents',50,36,23,colour=MUTED)
label('ZHAW Institut für Wirtschaftsinformatik',884,30,27,True)
# Vector ZHAW logo is placed after ReportLab generation.
line(25,52,1164,52)

# Title and motivation.
label('Human Robot Coordination',25,75,109,True)
label('What would make this robot useful to you?',25,119,44,True)
para('A robot can perform remarkable actions. Making them useful means agreeing what people need, in a particular situation.',
     25,141,1110,29,max_h=14)
para('<b>Many everyday tasks need coordination before, during and after execution.</b>',25,156,1110,29,max_h=14)

# Handwritten correction beside the title leaves room for seven readable guidelines.
PROVOCATION_X,PROVOCATION_Y,PROVOCATION_W=780,78,384
label('Supporting human-robot coordination',PROVOCATION_X,PROVOCATION_Y,29,True)
label('based on',PROVOCATION_X,PROVOCATION_Y+24,35,colour=MUTED)
pdf.saveState();pdf.translate((PROVOCATION_X+2)*mm,HEIGHT-(PROVOCATION_Y+22)*mm);pdf.rotate(5)
pdf.setFont('Hand',33);pdf.setFillColor(HexColor('#b44e00'));pdf.drawString(0,0,'despite');pdf.restoreState()
label('generative AI',PROVOCATION_X+65,PROVOCATION_Y+24,35,True)
line(PROVOCATION_X,PROVOCATION_Y+29,PROVOCATION_X+59,PROVOCATION_Y+34,'#b44e00',2)
line(PROVOCATION_X+2,PROVOCATION_Y+35,PROVOCATION_X+61,PROVOCATION_Y+28,'#b44e00',1.8)
para('Generative AI supports flexible dialogue.<br/>Explicit state and control preserve the agreed task.',
     PROVOCATION_X,PROVOCATION_Y+43,PROVOCATION_W,25,max_h=25)

label('Agree. Act. Adapt.',25,183,46,True)
label('A flexible workflow for human-robot coordination',614,188,28,colour=MUTED)
svg(ASSETS/'coordination-workflow-print.svg',25,205,1139)

# The lower area has an editorial column and a full, two-column dialogue.
LEFT_X,LEFT_W,RIGHT_X,RIGHT_W=25,444,490,674
TOP=420
label('Seven design guidelines',LEFT_X,TOP,40,True)
guideline_section=(ROOT/'POSTER.md').read_text(encoding='utf-8').split('## Seven design guidelines\n',1)[1].split('\n## ',1)[0]
guidelines=re.findall(r'^\d+\. \*\*(.+?)\*\* (.+)$',guideline_section,re.MULTILINE)
if len(guidelines)!=7:
    raise ValueError('Expected seven numbered guidelines in POSTER.md')
for i,(heading,body) in enumerate(guidelines):
    x=LEFT_X;y=443+i*31.2;card_h=29.2
    content=f'<font size="25"><b>{escape(heading)}</b></font> {escape(body)}'
    style=ParagraphStyle('guideline',fontName='SG-Regular',fontSize=24,leading=30,textColor=HexColor(INK))
    p=Paragraph(content,style);_,height=p.wrap(410*mm,10000)
    if height/mm>card_h-8:
        raise ValueError(f'Guideline {i+1} exceeds the available card height')
    box(x,y,LEFT_W,card_h,'#ffffff',LINE,r=3)
    label(f'{i+1:02}',x+7,y+11,19,True,DEEP_TEAL)
    p.drawOn(pdf,(x+24)*mm,HEIGHT-(y+(card_h-height/mm)/2)*mm-height)
    measurements.append({'guideline':i+1,'text':heading+' '+body,'x':x,'y':y,'width':LEFT_W,'height':card_h,'heading_font_pt':25,'body_font_pt':24})

# Compact current challenges panel, replacing the booth invitation.
CY=666
box(LEFT_X,CY,LEFT_W,142,'#fff4e9',r=4)
label('CURRENT CHALLENGE',LEFT_X+9,CY+8,19,True,'#a24900')
para('Fast replies, faithful coordination',LEFT_X+9,CY+19,LEFT_W-18,34,bold=True,max_h=15)
para('Natural conversation needs quick replies. Coordination also needs dependable meaning and control.',
     LEFT_X+9,CY+36,LEFT_W-18,24,max_h=23)
challenges=[
    ('Beyond speech','Connect speech, visual cues and planned actions, including behaviour initiated by the robot.'),
    ('Keep speech aligned','Keep spoken responses faithful to the current agreement as workflow stages and instructions change.'),
    ('Check communication','Confirm meaning and check actual speech; transcripts alone cannot guarantee either.')]
for i,(heading,body) in enumerate(challenges):
    x=LEFT_X+9+i*144
    label(heading,x,CY+60,23,True)
    para(body,x,CY+70,132,23,max_h=42)
para('<b>Next:</b> Measure and reduce response delays while preserving task control and checking spoken delivery.',
     LEFT_X+9,CY+119,LEFT_W-18,22,max_h=19)

# Full conversation: no summary, omission, rewriting or fabricated stage messages.
label('Coordination in practice',RIGHT_X,TOP,40,True)
para('Our Coordination Agent puts these guidelines into practice in PROMETHEUS.',
     RIGHT_X,TOP+20,RIGHT_W,26,max_h=14)
messages=json.loads((ASSETS/'valerian-interaction-full.json').read_text(encoding='utf-8'))
assert len(messages)==18
ends=[]
for col,(start,end) in enumerate([(0,9),(9,18)]):
    x=RIGHT_X+col*344;w=330
    label('01 / Explore and clarify' if col==0 else '02 / Agree, act and return',x,TOP+38,22,True,DEEP_TEAL)
    y=TOP+52
    for index in range(start,end):
        message=messages[index];human=message['speaker']=='Human'
        content=message['text']
        style=ParagraphStyle('dialogue',fontName='SG-Regular',fontSize=24,leading=29.5,textColor=HexColor(INK))
        p=Paragraph(escape(content),style);_,height=p.wrap((w-16)*mm,10000)
        h=height/mm+12
        box(x,y,w,h,'#ffffff' if human else '#e7f2ef',LINE if human else None,r=3)
        label(f'{index+1:02}  '+('HUMAN' if human else 'VALERIAN'),x+8,y+4,16,True,MUTED if human else DEEP_TEAL)
        p.drawOn(pdf,(x+8)*mm,HEIGHT-(y+10)*mm-height)
        measurements.append({'message':index+1,'x':x,'y':y,'height':h,'font_pt':24})
        y+=h+2
    ends.append(y)
    if y>791:
        raise ValueError(f'Full dialogue column {col+1} ends at {y:.1f} mm; must end before 791 mm')
para('Complete recorded Valerian interaction. Wording and order preserved; typeset for print. The personal story is fictional.',
     RIGHT_X,795,RIGHT_W,19,max_h=15)

line(25,816,1164,816)
label('Design guidelines informed by iterative implementation and scenario testing.',25,821,20,colour=MUTED)
label('SIRA Lab  /  PROMETHEUS',1020,821,20,True,DEEP_TEAL)
pdf.showPage();pdf.save()

# Insert only the extracted logo's own paths; no hidden source-poster content.
doc=fitz.open(TMP/'poster-base.pdf');logo=fitz.open(ASSETS/'zhaw-logo.pdf')
doc[0].show_pdf_page(fitz.Rect(1128*mm,19*mm,1161*mm,48*mm),logo,0,keep_proportion=True)
final=OUT/'human-robot-coordination-a0-landscape.pdf'
doc.save(final,garbage=4,deflate=True)
check=fitz.open(final)
assert len(check)==1
assert abs(check[0].rect.width-WIDTH)<.01 and abs(check[0].rect.height-HEIGHT)<.01
# Report layout measurements for reproducibility and text-content verification.
(TMP/'layout-check.json').write_text(json.dumps({'pages':len(check),'width_mm':WIDTH/mm,'height_mm':HEIGHT/mm,
    'guidelines':len(guidelines),'dialogue_messages':len(messages),'dialogue_column_end_mm':ends,'measurements':measurements},indent=2),encoding='utf-8')
print(json.dumps({'pdf':str(final),'page_mm':[WIDTH/mm,HEIGHT/mm],'messages':len(messages),'dialogue_ends_mm':ends}))
