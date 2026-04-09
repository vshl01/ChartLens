declare module 'react-native-vector-icons/MaterialCommunityIcons' {
  import type {Component} from 'react';
  import type {TextProps} from 'react-native';
  export interface IconProps extends TextProps {
    name: string;
    size?: number;
    color?: string;
  }
  export default class Icon extends Component<IconProps> {}
}
