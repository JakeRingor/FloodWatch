from google.colab import files, output
from PIL import Image, ImageOps, ImageDraw
from IPython.display import display, clear_output
import ipywidgets as widgets
import io, math

output.enable_custom_widget_manager()

def estimate(top, bottom, waterline, reliable, suitable, diameter=None):
    if not reliable or not suitable:
        return None, 'Unable to estimate: confirm the full tire boundaries and suitable view first.'
    if bottom <= top:
        return None, 'Wheel bottom must be below wheel top.'
    if not top <= waterline <= bottom:
        return None, 'Waterline is outside the tire height. No depth estimate for this setup.'
    fraction = (bottom - waterline) / (bottom - top)
    if diameter is not None and (not math.isfinite(diameter) or diameter <= 0):
        return None, 'Enter a positive measured outside tire diameter, or turn off Known diameter.'
    depth = None if diameter is None else fraction * diameter
    return {'height_fraction': fraction, 'depth_cm': depth}, ''

print('Choose ONE JPG or PNG. Use a side-on wheel photo with little camera tilt.')
uploaded = files.upload()
if not uploaded:
    raise ValueError('No photo uploaded. Run this cell again.')
filename = next(iter(uploaded))
with Image.open(io.BytesIO(uploaded[filename])) as source:
    photo = ImageOps.exif_transpose(source).convert('RGB')
photo.thumbnail((900, 650))
w, h = photo.size
if min(w, h) < 10:
    raise ValueError('Image is too small.')

def slider(label, value, maximum):
    return widgets.IntSlider(description=label, value=value, min=0, max=maximum,
                             continuous_update=False,
                             style={'description_width': '120px'},
                             layout=widgets.Layout(width='95%'))

left = slider('Wheel left', w // 3, w-1)
right = slider('Wheel right', 2*w // 3, w-1)
top = slider('Wheel top', h // 3, h-1)
bottom = slider('Wheel bottom', 2*h // 3, h-1)
water = slider('Waterline', h // 2, h-1)
reliable = widgets.Checkbox(description='I can locate the FULL tire top and bottom reliably.', indent=False)
suitable = widgets.Checkbox(description='Side-on view, little camera tilt; tire rests on the road.', indent=False)
known = widgets.Checkbox(description='I know the measured outside tire diameter.', indent=False)
diameter = widgets.FloatText(value=0, description='Diameter (cm)', disabled=True,
                            style={'description_width': '120px'})
preview = widgets.Output()

def render(change=None):
    diameter.disabled = not known.value
    canvas = photo.copy()
    draw = ImageDraw.Draw(canvas)
    valid_box = left.value < right.value and top.value < bottom.value
    if valid_box:
        draw.rectangle((left.value, top.value, right.value, bottom.value), outline='red', width=3)
    draw.line((0, water.value, w-1, water.value), fill='cyan', width=3)
    draw.text((8, max(0, water.value-15)), 'Waterline', fill='blue', stroke_width=1, stroke_fill='white')
    result, reason = estimate(top.value, bottom.value, water.value,
                              reliable.value, suitable.value,
                              diameter.value if known.value else None)
    with preview:
        clear_output(wait=True)
        display(canvas)
        print('Manual-assisted estimate — not automatic water detection.')
        if not valid_box:
            print('Move the boundaries so left < right and top < bottom.')
        elif reason:
            print(reason)
        else:
            print(f"Estimated submerged HEIGHT: {100*result['height_fraction']:.1f}%")
            if result['depth_cm'] is None:
                print('Depth in centimeters unavailable: outside tire diameter is unknown.')
            else:
                print(f"Approximate depth at this tire: {result['depth_cm']:.1f} cm")
        print('Red box = full outside tire. Cyan line = water surface at that tire.')

for control in (left, right, top, bottom, water, reliable, suitable, known, diameter):
    control.observe(render, names='value')

display(widgets.HTML('<b>1.</b> Position the red box around the full tire, not just the rim or visible part.<br>'
                     '<b>2.</b> Move the cyan line to the water surface where it meets this tire.<br>'
                     '<b>3.</b> Confirm the boundaries/view only if justified. Leave diameter unknown unless measured.<br>'
                     'If the submerged bottom is hidden and cannot be located reliably, leave the first checkbox off.'))
display(widgets.VBox([left, right, top, bottom, water, reliable, suitable, known, diameter, preview]))
render()
