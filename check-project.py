#!/usr/bin/env python3
"""Resource/manifest consistency checks, NOT an Android build or a security audit."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent
main = root / 'app/src/main'
res = main / 'res'
xmls = list(main.rglob('*.xml'))
for f in xmls: ET.parse(f)
manifest = ET.parse(main / 'AndroidManifest.xml').getroot()
a = '{http://schemas.android.com/apk/res/android}'
permissions = {x.attrib[a+'name'] for x in manifest.findall('uses-permission')}
assert permissions == {'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.RECEIVE_BOOT_COMPLETED','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_DATA_SYNC'}
app=manifest.find('application')
assert app.attrib[a+'allowBackup']=='false'
assert app.attrib[a+'debuggable']=='false'
assert app.attrib[a+'usesCleartextTraffic']=='false'
for tag in ['activity','service','receiver']:
    for node in app.findall(tag):
        name=node.attrib[a+'name'].lstrip('.')
        assert (main/'java/dev/yerin/weeklymeter'/f'{name}.java').is_file(),name
assert app.find('service').attrib[a+'permission']=='android.permission.BIND_JOB_SERVICE'
login=next(n for n in app.findall('service') if n.attrib[a+'name']=='.BrowserLoginService')
assert login.attrib[a+'exported']=='false' and login.attrib[a+'foregroundServiceType']=='dataSync'
manual=next(n for n in app.findall('service') if n.attrib[a+'name']=='.WidgetRefreshService')
assert manual.attrib[a+'exported']=='false' and manual.attrib[a+'foregroundServiceType']=='dataSync'
widget_source=(main/'java/dev/yerin/weeklymeter/WeeklyWidget.java').read_text(encoding='utf-8')
assert 'PendingIntent.getForegroundService(c,1,refresh,' in widget_source
assert 'new Intent(c,WidgetRefreshService.class)' in widget_source
for key in ['automatic_padding','padding_horizontal_dp','padding_vertical_dp','row_gap_dp']:
    assert widget_source.count('"'+key+'"')==2, 'padding persistence read/write: '+key
settings_source=(main/'java/dev/yerin/weeklymeter/WidgetStyleSettingsActivity.java').read_text(encoding='utf-8')
assert 'new Repo(' not in settings_source and 'Scheduler.request(' not in settings_source
main_source=(main/'java/dev/yerin/weeklymeter/MainActivity.java').read_text(encoding='utf-8')
resume=next(line for line in main_source.splitlines() if 'void onResume()' in line)
assert 'reconcileConnection()' in resume and '.sync(' not in resume, 'app resume must not fetch usage'
widget=ET.parse(res/'xml/weekly_widget_info.xml').getroot()
assert widget.attrib[a+'targetCellWidth']=='1' and widget.attrib[a+'targetCellHeight']=='1'
assert widget.attrib[a+'resizeMode']=='horizontal|vertical'
layout=ET.parse(res/'layout/weekly_widget.xml').getroot()
assert not any(n.tag in {'Button','ProgressBar'} for n in layout.iter())
known=set()
for f in res.rglob('*.xml'):
    if f.parent.name!='values':known.add((f.parent.name,f.stem))
    for node in ET.parse(f).getroot().iter():
        if 'name' in node.attrib:known.add((node.tag,node.attrib['name']))
        for v in node.attrib.values():
            if v.startswith('@+id/'):known.add(('id',v[5:]))
for f in xmls:
    for t,n in re.findall(r'@(?:\+)?(\w+)/(\w+)',f.read_text(encoding='utf-8')):
        assert (t,n) in known,(f,t,n)
for f in (main/'java').rglob('*.java'):
    for t,n in re.findall(r'(?<!android\.)\bR\.(\w+)\.(\w+)',f.read_text(encoding='utf-8')):
        assert (t,n) in known,(f,t,n)
    if 'new URL(' in f.read_text(encoding='utf-8'):assert f.name=='Api.java'
    assert 'android.webkit' not in f.read_text(encoding='utf-8')
print(f'PASS: {len(xmls)} XML files, resource references, manifest components, and restricted permissions. No device/build verification.')
