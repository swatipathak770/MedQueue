import { createSlice } from '@reduxjs/toolkit'

const initialState = {
  token: localStorage.getItem('medqueue.token'),
  user: JSON.parse(localStorage.getItem('medqueue.user') || 'null'),
}
const authSlice = createSlice({
  name: 'auth', initialState,
  reducers: {
    signedIn(state, action) {
      state.token = action.payload.token; state.user = action.payload.user
      localStorage.setItem('medqueue.token', state.token); localStorage.setItem('medqueue.user', JSON.stringify(state.user))
    },
    signedOut(state) {
      state.token = null; state.user = null
      localStorage.removeItem('medqueue.token'); localStorage.removeItem('medqueue.user')
    },
  },
})
export const { signedIn, signedOut } = authSlice.actions
export default authSlice.reducer
