import FamilyControls
import SwiftUI

/// Apple's app picker in a sheet. `onDone(nil)` means the user cancelled.
struct AppPickerView: View {
  @State var selection: FamilyActivitySelection
  let onDone: (FamilyActivitySelection?) -> Void

  var body: some View {
    NavigationView {
      FamilyActivityPicker(selection: $selection)
        .toolbar {
          ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { onDone(nil) }
          }
          ToolbarItem(placement: .confirmationAction) {
            Button("Done") { onDone(selection) }
          }
        }
    }
    .navigationViewStyle(.stack)
  }
}
