from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.clock import Clock
from kivy.graphics import Color, Rectangle

# Plyer TTS (Bolne ke liye)
try:
    from plyer import tts
except Exception:
    tts = None

class JarvisUI(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisUI, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 30
        self.spacing = 20

        # Background color set karna (Dark theme)
        with self.canvas.before:
            Color(0.05, 0.05, 0.08, 1)
            self.rect = Rectangle(size=self.size, pos=self.pos)
        self.bind(size=self._update_rect, pos=self._update_rect)

        # Status Label (Jo screen par dikhega)
        self.status_label = Label(
            text="[color=00ff66][b]Jarvis Tayar Hai...[/b][/color]",
            markup=True,
            font_size='24sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        # Talk / Speak Button (Jab aap dabayein ya automatic chale)
        self.speak_btn = Button(
            text="Time, Date aur Din Suno",
            font_size='18sp',
            size_hint=(1, 0.3),
            background_color=(0.1, 0.6, 0.3, 1)
        )
        self.speak_btn.bind(on_press=self.tell_time_date_day)
        self.add_widget(self.speak_btn)

        # App khulte hi 2 sekund baad automatic bolna shuru karega
        Clock.schedule_once(self.tell_time_date_day, 2)

    def _update_rect(self, instance, value):
        self.rect.pos = instance.pos
        self.rect.size = instance.size

    def speak(self, text):
        if tts:
            try:
                tts.speak(text)
            except Exception as e:
                print("TTS Error:", e)

    def tell_time_date_day(self, dt):
        now = datetime.now()
        
        # Time, Date aur Day nikalna
        current_time = now.strftime("%I:%M %p")
        current_date = now.strftime("%d %B %Y")
        current_day = now.strftime("%A")
        
        # Assistant ka poora solid jawab
        reply = f"Sir, abhi time {current_time} ho raha hai, aaj ki tarikh {current_date} hai, aur din {current_day} hai."
        
        # Screen par update karna
        self.status_label.text = f"[color=00f0ff][b]Time: {current_time}\nDin: {current_day}\nTarikh: {current_date}[/b][/color]"
        
        # Bol kar batana
        self.speak(reply)


class TestApp(App):
    def build(self):
        return JarvisUI()


if __name__ == '__main__':
    TestApp().run()
    
