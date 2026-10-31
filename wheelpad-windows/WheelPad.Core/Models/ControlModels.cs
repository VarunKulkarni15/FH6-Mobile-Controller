using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace WheelPad.Core.Models
{
    [JsonConverter(typeof(JsonStringEnumConverter))]
    public enum InputType
    {
        AnalogAxis,
        AnalogTrigger,
        DigitalButton
    }

    [JsonConverter(typeof(JsonStringEnumConverter))]
    public enum BindingType
    {
        XboxController,
        KeyboardKey,
        Composite
    }

    [JsonConverter(typeof(JsonStringEnumConverter))]
    public enum XboxButton
    {
        None,
        A,
        B,
        X,
        Y,
        LeftBumper,
        RightBumper,
        Back,
        Start,
        Guide,
        LeftThumbClick,
        RightThumbClick,
        DpadUp,
        DpadDown,
        DpadLeft,
        DpadRight,
        LeftThumbX,
        RightThumbX,
        LeftTrigger,
        RightTrigger
    }

    [JsonConverter(typeof(JsonStringEnumConverter))]
    public enum InputBehavior
    {
        Instant,
        Hold,
        Toggle,
        Repeat
    }

    [JsonConverter(typeof(JsonStringEnumConverter))]
    public enum ControlShape
    {
        CircleWheel,
        VerticalPedal,
        RoundedButton,
        WideBar,
        PillToggle
    }

    public class GameAction
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = Guid.NewGuid().ToString("N");

        [JsonPropertyName("name")]
        public string Name { get; set; } = string.Empty;

        [JsonPropertyName("category")]
        public string Category { get; set; } = "General";

        [JsonPropertyName("inputType")]
        public InputType InputType { get; set; } = InputType.DigitalButton;

        [JsonPropertyName("bindingType")]
        public BindingType BindingType { get; set; } = BindingType.XboxController;

        [JsonPropertyName("xboxTarget")]
        public XboxButton XboxTarget { get; set; } = XboxButton.None;

        [JsonPropertyName("keyboardKeyCode")]
        public int KeyboardKeyCode { get; set; } = 0;

        [JsonPropertyName("keyboardKeyName")]
        public string KeyboardKeyName { get; set; } = string.Empty;

        [JsonPropertyName("behavior")]
        public InputBehavior Behavior { get; set; } = InputBehavior.Instant;

        [JsonPropertyName("repeatIntervalMs")]
        public int RepeatIntervalMs { get; set; } = 50;

        [JsonPropertyName("isCustom")]
        public bool IsCustom { get; set; } = false;

        [JsonPropertyName("description")]
        public string Description { get; set; } = string.Empty;

        public GameAction Clone()
        {
            return (GameAction)MemberwiseClone();
        }
    }

    public class TouchControlElement
    {
        [JsonPropertyName("elementId")]
        public string ElementId { get; set; } = Guid.NewGuid().ToString("N");

        [JsonPropertyName("actionId")]
        public string ActionId { get; set; } = string.Empty;

        [JsonPropertyName("label")]
        public string Label { get; set; } = string.Empty;

        [JsonPropertyName("shape")]
        public ControlShape Shape { get; set; } = ControlShape.RoundedButton;

        [JsonPropertyName("normalizedX")]
        public float NormalizedX { get; set; }

        [JsonPropertyName("normalizedY")]
        public float NormalizedY { get; set; }

        [JsonPropertyName("normalizedWidth")]
        public float NormalizedWidth { get; set; }

        [JsonPropertyName("normalizedHeight")]
        public float NormalizedHeight { get; set; }

        [JsonPropertyName("opacity")]
        public float Opacity { get; set; } = 0.85f;

        [JsonPropertyName("accentColorHex")]
        public string AccentColorHex { get; set; } = "#00E5FF";

        [JsonPropertyName("hapticFeedback")]
        public bool HapticFeedback { get; set; } = true;

        [JsonPropertyName("isEnabled")]
        public bool IsEnabled { get; set; } = true;

        public TouchControlElement Clone()
        {
            return (TouchControlElement)MemberwiseClone();
        }
    }

    public class SteeringConfig
    {
        [JsonPropertyName("maxRotationDegrees")]
        public int MaxRotationDegrees { get; set; } = 540;

        [JsonPropertyName("deadzonePercent")]
        public float DeadzonePercent { get; set; } = 0.02f;

        [JsonPropertyName("gammaCurve")]
        public float GammaCurve { get; set; } = 1.15f;

        [JsonPropertyName("returnToCenterSpeed")]
        public float ReturnToCenterSpeed { get; set; } = 0.85f;

        [JsonPropertyName("useGyroSteering")]
        public bool UseGyroSteering { get; set; } = false;
    }

    public class ControllerProfile
    {
        [JsonPropertyName("profileId")]
        public string ProfileId { get; set; } = Guid.NewGuid().ToString("N");

        [JsonPropertyName("profileName")]
        public string ProfileName { get; set; } = "New Profile";

        [JsonPropertyName("isDefault")]
        public bool IsDefault { get; set; } = false;

        [JsonPropertyName("lastModifiedUtc")]
        public DateTime LastModifiedUtc { get; set; } = DateTime.UtcNow;

        [JsonPropertyName("steeringConfig")]
        public SteeringConfig SteeringConfig { get; set; } = new();

        [JsonPropertyName("actions")]
        public List<GameAction> Actions { get; set; } = new();

        [JsonPropertyName("layout")]
        public List<TouchControlElement> Layout { get; set; } = new();

        public ControllerProfile Clone()
        {
            var clone = (ControllerProfile)MemberwiseClone();
            clone.ProfileId = Guid.NewGuid().ToString("N");
            clone.ProfileName = $"{ProfileName} (Copy)";
            clone.Actions = new List<GameAction>(Actions.ConvertAll(a => a.Clone()));
            clone.Layout = new List<TouchControlElement>(Layout.ConvertAll(l => l.Clone()));
            clone.LastModifiedUtc = DateTime.UtcNow;
            return clone;
        }
    }
}

