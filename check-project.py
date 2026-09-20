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
assert permissions == {'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.RECEIVE_BOOT_COMPLETED','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_DATA_SYNC','android.permission.SYSTEM_ALERT_WINDOW','android.permission.FOREGROUND_SERVICE_SPECIAL_USE'}
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
floating=next(n for n in app.findall('service') if n.attrib[a+'name']=='.FloatingWidgetService')
assert floating.attrib[a+'exported']=='false' and floating.attrib[a+'foregroundServiceType']=='specialUse'
assert floating.find('property').attrib[a+'name']=='android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE'
widget_source=(main/'java/dev/yerin/weeklymeter/WeeklyWidget.java').read_text(encoding='utf-8')
assert 'PendingIntent.getForegroundService(c,widgetId<0?1:widgetId,refresh,' in widget_source
assert 'new Intent(c,WidgetRefreshService.class)' in widget_source
assert 'WidgetRefreshService.ACTION_HOME_TAP' in widget_source and 'WidgetRefreshService.EXTRA_APP_WIDGET_ID,widgetId' in widget_source
appearance_source=(main/'java/dev/yerin/weeklymeter/WidgetAppearance.java').read_text(encoding='utf-8')
for key in ['automatic_padding','padding_horizontal_dp','padding_vertical_dp','row_gap_dp']:
    assert appearance_source.count('"'+key+'"')==2, 'padding persistence read/write: '+key
settings_source=(main/'java/dev/yerin/weeklymeter/WidgetStyleSettingsActivity.java').read_text(encoding='utf-8')
assert 'new Repo(' not in settings_source and 'Scheduler.request(' not in settings_source
assert 'FloatingPreferences.style(this)' in settings_source and 'FloatingPreferences.saveStyle(this,' in settings_source
floating_source=(main/'java/dev/yerin/weeklymeter/FloatingWidgetService.java').read_text(encoding='utf-8')
assert 'TYPE_APPLICATION_OVERLAY' in floating_source and 'FLAG_NOT_FOCUSABLE' in floating_source
assert 'new Repo(' not in floating_source and '.sync(' not in floating_source, 'overlay renders cache; no independent polling'
assert 'START_NOT_STICKY' in floating_source and 'RECEIVER_NOT_EXPORTED' in floating_source
main_source=(main/'java/dev/yerin/weeklymeter/MainActivity.java').read_text(encoding='utf-8')
resume_start=main_source.index('void onResume()')
resume_end=main_source.index('@Override protected void onPause()',resume_start)
resume=main_source[resume_start:resume_end]
assert 'reconcileConnection()' in resume and '.sync(' not in resume, 'app resume must not fetch usage'
status_start=main_source.index('void automaticStatus()')
status_end=main_source.index('void languageHeader()',status_start)
status_ui=main_source[status_start:status_end]
assert 'new Repo(' not in status_ui and '.sync(' not in status_ui and 'Scheduler.ensure(' not in status_ui, 'status/settings must not fetch or reset jobs'
assert 'BackgroundAccess.read(this)' in status_ui and 'AutoRefreshDiagnostics.read(this)' in status_ui
assert 'BackgroundAccess.batterySettings(this)' in status_ui and 'Settings.ACTION_APPLICATION_DETAILS_SETTINGS' in status_ui
assert 'ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS' not in main_source, 'battery exceptions require user-controlled system settings'
interval_ui=main_source[main_source.index('void refreshInterval()'):main_source.index('void floatingOptions()')]
assert 'RefreshInterval.parse(' in interval_ui and 'Scheduler.ensure(' in interval_ui
assert 'new Repo(' not in interval_ui and '.sync(' not in interval_ui, 'changing interval must not fetch usage'
widget=ET.parse(res/'xml/weekly_widget_info.xml').getroot()
assert widget.attrib[a+'targetCellWidth']=='1' and widget.attrib[a+'targetCellHeight']=='1'
assert widget.attrib[a+'resizeMode']=='horizontal|vertical'
layout=ET.parse(res/'layout/weekly_widget.xml').getroot()
assert not any(n.tag in {'Button','ProgressBar'} for n in layout.iter())
known=set()
for f in res.rglob('*.xml'):
    resource_type=f.parent.name.split('-')[0]
    if resource_type!='values':known.add((resource_type,f.stem))
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
