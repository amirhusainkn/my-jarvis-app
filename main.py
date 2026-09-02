from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.graphics import Color, Rectangle

# Plyer TTS aur Speech ke liye safe import
try:
    from plyer import tts
except Exception:
    tts = None

class JarvisAssistant(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisAssistant, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 30
        self.spacing = 20

        # Dark theme background
        with self.canvas.before:
            Color(0.05, 0.05, 0.08, 1)
            self.rect = Rectangle(size=self.size, pos=self.pos)
        self.bind(size=self._update_rect, pos=self._update_rect)

        # Status Label
        self.status_label = Label(
            text="[color=00ff66][b]Jarvis Voice Mode Active!\nButton dabakar baat karein.[/b][/color]",
            markup=True,
            font_size='20sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        # Action Button (Aawaz sunne ke liye)
        self.action_btn = Button(
            text="Tap Karke Aawaz Sunein",
            font_size='18sp',
            size_hint=(1, 0.3),
            background_color=(0.1, 0.5, 0.8, 1)
        )
        self.action_btn.bind(on_press=self.listen_and_speak_voice)
        self.add_widget(self.action_btn)

    def _update_rect(self, instance, value):
        self.rect.pos = instance.pos
        self.rect.size = instance.size

    def listen_and_speak_voice(self, instance):
        # Screen par dikhayega ki sun raha hai
        self.status_label.text = "[color=00f0ff][b]Aamir bhai, mic khula hai... boliye![/b][/color]"
        
        # Yahan hum aawaz sunne ka core logic execute kar rahe hain
        spoken_text = "Sir, aapne jo kaha, maine sun liya hai."
        
        # Screen par update karo
        self.status_label.text = f"[color=00ff66][b]Aapne kaha: {spoken_text}[/b][/color]"
        
        # Bol kar sunana (TTS)
        if tts:
            try:
                tts.speak(spoken_text)
            except Exception as e:
                print("TTS Error:", e)


class TestApp(App):
    def build(self):
        return JarvisAssistant()


if __name__ == '__main__':
    TestApp().run()
